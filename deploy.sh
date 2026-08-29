#!/bin/bash

set -e -o pipefail  # exit ngay khi có lỗi, kể cả lỗi ở giữa pipeline

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

APP_DIR="/opt/stockspace"           # Thư mục chứa project trên VPS
REPO_URL="https://github.com/vinhleechem/StockSpace_BE.git"   # Repository URL
BRANCH="main"                       # Branch muốn deploy

setup() {
    log_info "=== Bắt đầu setup môi trường VPS ==="

    log_info "Cập nhật package list..."
    apt-get update -y && apt-get upgrade -y

    if ! command -v docker &> /dev/null; then
        log_info "Cài Docker..."
        curl -fsSL https://get.docker.com | sh
        systemctl enable docker
        systemctl start docker
        log_success "Docker đã cài xong: $(docker --version)"
    else
        log_success "Docker đã có: $(docker --version)"
    fi

    if ! docker compose version &> /dev/null; then
        log_info "Cài Docker Compose plugin..."
        apt-get install -y docker-compose-plugin
        log_success "Docker Compose: $(docker compose version)"
    else
        log_success "Docker Compose đã có: $(docker compose version)"
    fi

    if ! command -v git &> /dev/null; then
        log_info "Cài Git..."
        apt-get install -y git
    fi

    log_info "Tạo thư mục $APP_DIR..."
    mkdir -p "$APP_DIR"

    if [ ! -d "$APP_DIR/.git" ]; then
        log_info "Clone repository..."
        git clone -b "$BRANCH" "$REPO_URL" "$APP_DIR"
        log_success "Clone thành công vào $APP_DIR"
    else
        log_warn "Repo đã tồn tại, bỏ qua clone."
    fi

    if [ ! -f "$APP_DIR/.env" ]; then
        log_warn "Chưa có file .env — copy từ .env.example..."
        cp "$APP_DIR/.env.example" "$APP_DIR/.env"
        log_warn ">>> QUAN TRỌNG: Điền đầy đủ giá trị vào $APP_DIR/.env trước khi chạy deploy!"
        log_warn ">>> nano $APP_DIR/.env"
    else
        log_success "File .env đã tồn tại."
    fi

    mkdir -p "$APP_DIR/nginx/ssl"

    log_success "=== Setup hoàn tất! ==="
    log_info "Tiếp theo:"
    log_info "  1. Điền .env: nano $APP_DIR/.env"
    log_info "  2. Deploy:    bash $APP_DIR/deploy.sh deploy"
}

deploy() {
    log_info "=== Bắt đầu deploy StockSpace ==="

    cd "$APP_DIR"

    if [ ! -f ".env" ]; then
        log_error "Không tìm thấy .env! Chạy 'bash deploy.sh setup' trước."
    fi

    check_env

    log_info "Pull code từ branch $BRANCH..."
    git fetch origin
    git reset --hard "origin/$BRANCH"
    log_success "Code đã cập nhật."

    log_info "Khởi động PostgreSQL và chạy migration runner..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres
    DB_READY=false
    for ATTEMPT in $(seq 1 30); do
        if docker compose -f docker-compose.yml -f docker-compose.prod.yml \
            exec -T postgres pg_isready \
            -U "${DB_USERNAME:-postgres}" \
            -d "${DB_NAME:-stockspace}" > /dev/null 2>&1; then
            DB_READY=true
            break
        fi
        sleep 2
    done
    if [ "$DB_READY" != "true" ]; then
        log_error "PostgreSQL chưa sẵn sàng để chạy migration."
    fi
    if ! bash "$APP_DIR/ops/run-migrations.sh" --docker; then
        log_warn "Migration failed; running the read-only rental-contract preflight for diagnostics..."
        if [ -f "$APP_DIR/ops/maintenance/rental_contract_refactor_preflight.sql" ]; then
            docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                exec -T postgres psql \
                -X -v ON_ERROR_STOP=1 \
                -U "${DB_USERNAME:-postgres}" \
                -d "${DB_NAME:-stockspace}" \
                < "$APP_DIR/ops/maintenance/rental_contract_refactor_preflight.sql" \
                || log_warn "Rental-contract diagnostic query could not be completed."
        fi
        log_error "Production migration failed; application containers were left unchanged."
    fi
    log_success "Production migrations completed."

    log_info "Build image và khởi động containers..."
    docker compose pull postgres nginx 2>/dev/null || true  # Pull image mới nhất từ registry
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans

    log_info "Đợi ứng dụng khởi động (tối đa 120s)..."
    TIMEOUT=120
    ELAPSED=0
    until docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T app wget -q -O /dev/null http://localhost:8080/actuator/health > /dev/null 2>&1; do
        sleep 5
        ELAPSED=$((ELAPSED + 5))
        if [ $ELAPSED -ge $TIMEOUT ]; then
            log_error "App không khởi động được sau ${TIMEOUT}s. Xem log: docker compose logs app"
        fi
        echo -n "."
    done
    echo ""

    log_info "Dọn dẹp Docker images không dùng..."
    docker image prune -f

    log_success "=== Deploy thành công! ==="
    docker compose ps
}

show_logs() {
    cd "$APP_DIR"
    SERVICE=${2:-app}  # Mặc định xem log của app, truyền 'postgres'/'nginx' để xem service khác
    log_info "Xem log service: $SERVICE (Ctrl+C để thoát)"
    docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f "$SERVICE"
}

restart_service() {
    cd "$APP_DIR"
    SERVICE=${2:-app}
    log_info "Restart service: $SERVICE"
    docker compose -f docker-compose.yml -f docker-compose.prod.yml restart "$SERVICE"
    log_success "Đã restart $SERVICE"
}

stop_all() {
    cd "$APP_DIR"
    log_warn "Dừng toàn bộ services..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml down
    log_success "Đã dừng tất cả services."
}

show_status() {
    cd "$APP_DIR"
    log_info "=== Trạng thái containers ==="
    docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
    echo ""
    log_info "=== Disk Usage ==="
    docker system df
}

backup_db() {
    cd "$APP_DIR"
    source .env
    BACKUP_DIR="$APP_DIR/backups"
    mkdir -p "$BACKUP_DIR"
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP_FILE="$BACKUP_DIR/stockspace_${TIMESTAMP}.sql.gz"

    log_info "Backup database → $BACKUP_FILE"
    docker compose exec -T postgres pg_dump \
        -U "${DB_USERNAME:-postgres}" \
        "${DB_NAME:-stockspace}" | gzip > "$BACKUP_FILE"

    log_success "Backup xong: $BACKUP_FILE"
    log_info "Các backup hiện có:"
    ls -lh "$BACKUP_DIR"
}

reset_database() {
    if [ "${CONFIRM_PRODUCTION_DB_RESET:-}" != "RESET_STOCKSPACE_PRODUCTION" ]; then
        log_error "Từ chối xóa dữ liệu: đặt CONFIRM_PRODUCTION_DB_RESET=RESET_STOCKSPACE_PRODUCTION để xác nhận."
    fi

    cd "$APP_DIR"
    if [ ! -f ".env" ]; then
        log_error "Không tìm thấy .env! Không thể backup hoặc reset database."
    fi
    check_env

    log_warn "=== RESET TOÀN BỘ DỮ LIỆU NGHIỆP VỤ PRODUCTION ==="
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres

    DB_READY=false
    for ATTEMPT in $(seq 1 30); do
        if docker compose -f docker-compose.yml -f docker-compose.prod.yml \
            exec -T postgres pg_isready \
            -U "${DB_USERNAME:-postgres}" \
            -d "${DB_NAME:-stockspace}" > /dev/null 2>&1; then
            DB_READY=true
            break
        fi
        sleep 2
    done
    if [ "$DB_READY" != "true" ]; then
        log_error "PostgreSQL chưa sẵn sàng; chưa có dữ liệu nào bị xóa."
    fi

    log_info "Kiểm tra schema và baseline các migration lịch sử trước khi xóa dữ liệu..."
    ALLOW_MIGRATION_BASELINE=true \
        bash "$APP_DIR/ops/maintenance/migration_baseline.sh" --apply --docker
    log_success "Schema preflight và migration baseline đã hoàn tất."

    RUNNING_SERVICES="$(docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        ps --status running --services 2>/dev/null || true)"
    APP_WAS_RUNNING=false
    NGINX_WAS_RUNNING=false
    if grep -Fxq 'app' <<< "$RUNNING_SERVICES"; then APP_WAS_RUNNING=true; fi
    if grep -Fxq 'nginx' <<< "$RUNNING_SERVICES"; then NGINX_WAS_RUNNING=true; fi

    log_info "Dừng app/nginx để chặn ghi mới trong lúc backup và reset..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml stop app nginx 2>/dev/null || true

    BACKUP_DIR="$APP_DIR/backups"
    mkdir -p "$BACKUP_DIR"
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP_FILE="$BACKUP_DIR/pre_reset_${DB_NAME:-stockspace}_${TIMESTAMP}.sql.gz"
    log_info "Backup database trước khi reset → $BACKUP_FILE"

    if ! docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        exec -T postgres pg_dump \
        -U "${DB_USERNAME:-postgres}" \
        -d "${DB_NAME:-stockspace}" \
        --clean --if-exists --no-owner --no-privileges \
        | gzip > "$BACKUP_FILE"; then
        if [ "$APP_WAS_RUNNING" = "true" ]; then
            docker compose -f docker-compose.yml -f docker-compose.prod.yml start app || true
        fi
        if [ "$NGINX_WAS_RUNNING" = "true" ]; then
            docker compose -f docker-compose.yml -f docker-compose.prod.yml start nginx || true
        fi
        log_error "Backup thất bại; chưa có dữ liệu nào bị xóa và các service cũ đã được khôi phục."
    fi
    if [ ! -s "$BACKUP_FILE" ] || ! gzip -t "$BACKUP_FILE"; then
        if [ "$APP_WAS_RUNNING" = "true" ]; then
            docker compose -f docker-compose.yml -f docker-compose.prod.yml start app || true
        fi
        if [ "$NGINX_WAS_RUNNING" = "true" ]; then
            docker compose -f docker-compose.yml -f docker-compose.prod.yml start nginx || true
        fi
        log_error "File backup rỗng hoặc hỏng; chưa có dữ liệu nào bị xóa."
    fi
    log_success "Backup hợp lệ đã được lưu trên VPS."

    log_warn "Xóa toàn bộ dữ liệu trong các bảng public, giữ schema_migrations để bảo toàn lịch sử schema..."
    if ! docker compose -f docker-compose.yml -f docker-compose.prod.yml \
        exec -T postgres psql \
        -X -v ON_ERROR_STOP=1 \
        -U "${DB_USERNAME:-postgres}" \
        -d "${DB_NAME:-stockspace}" \
        < "$APP_DIR/ops/maintenance/reset_business_data.sql"
    then
        log_error "Reset database thất bại. App vẫn đang dừng; dùng backup $BACKUP_FILE nếu cần khôi phục."
    fi
    log_success "Toàn bộ dữ liệu nghiệp vụ production đã được xóa."

    deploy
}

check_env() {
    log_info "Kiểm tra biến môi trường..."
    source .env

    REQUIRED_VARS=("DB_PASSWORD" "JWT_SECRET" "CLOUDINARY_CLOUD_NAME" "CLOUDINARY_API_KEY" "CLOUDINARY_API_SECRET" "OPENROUTER_API_KEY" "OPENROUTER_MODEL")
    MISSING=()

    for VAR in "${REQUIRED_VARS[@]}"; do
        VALUE="${!VAR}"
        if [ -z "$VALUE" ] || [[ "$VALUE" == *"CHANGE_ME"* ]] || [[ "$VALUE" == *"your_"* ]]; then
            MISSING+=("$VAR")
        fi
    done

    if [ ${#MISSING[@]} -gt 0 ]; then
        log_error "Các biến sau chưa được điền trong .env: ${MISSING[*]}"
    fi
    if [ "${PUBLIC_HTTPS_READY:-false}" != "true" ]; then
        if [ "${ALLOW_INSECURE_HTTP:-false}" != "true" ]; then
            log_error "Production phải có HTTPS trước khi deploy (PUBLIC_HTTPS_READY=true). Chỉ dùng ALLOW_INSECURE_HTTP=true cho môi trường test tạm thời."
        fi
        log_warn "Đang deploy HTTP không mã hóa vì ALLOW_INSECURE_HTTP=true. Không dùng cấu hình này cho production public."
    fi

    log_success "Tất cả biến bắt buộc đã có."
}

print_usage() {
    echo ""
    echo "  StockSpace Deploy Script"
    echo ""
    echo "  Usage: bash deploy.sh <command>"
    echo ""
    echo "  Commands:"
    echo "    setup     — Cài Docker, clone repo, tạo .env (chạy 1 lần đầu)"
    echo "    deploy    — Pull code mới, build, restart services"
    echo "    logs      — Xem log (mặc định: app). VD: bash deploy.sh logs postgres"
    echo "    restart   — Restart service. VD: bash deploy.sh restart app"
    echo "    stop      — Dừng toàn bộ services"
    echo "    status    — Xem trạng thái containers"
    echo "    backup    — Backup database PostgreSQL"
    echo "    reset-db  — Backup, xóa toàn bộ dữ liệu production và deploy lại"
    echo ""
}

COMMAND=${1:-help}

case "$COMMAND" in
    setup)   setup ;;
    deploy)  deploy ;;
    logs)    show_logs "$@" ;;
    restart) restart_service "$@" ;;
    stop)    stop_all ;;
    status)  show_status ;;
    backup)  backup_db ;;
    reset-db) reset_database ;;
    *)       print_usage ;;
esac
