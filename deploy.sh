#!/bin/bash
# =============================================================================
# StockSpace — VPS Deploy Script
# Chạy lần đầu: bash deploy.sh setup
# Các lần sau:  bash deploy.sh deploy
# =============================================================================

set -e  # exit ngay khi có lỗi

# ── Màu sắc terminal ──────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ── Cấu hình ──────────────────────────────────────────────────────────────────
APP_DIR="/opt/stockspace"           # Thư mục chứa project trên VPS
REPO_URL="https://github.com/vinhleechem/StockSpace_BE.git"   # Repository URL
BRANCH="main"                       # Branch muốn deploy

# =============================================================================
# COMMAND: setup — Cài đặt môi trường lần đầu
# =============================================================================
setup() {
    log_info "=== Bắt đầu setup môi trường VPS ==="

    # 1. Cập nhật hệ thống
    log_info "Cập nhật package list..."
    apt-get update -y && apt-get upgrade -y

    # 2. Cài Docker
    if ! command -v docker &> /dev/null; then
        log_info "Cài Docker..."
        curl -fsSL https://get.docker.com | sh
        systemctl enable docker
        systemctl start docker
        log_success "Docker đã cài xong: $(docker --version)"
    else
        log_success "Docker đã có: $(docker --version)"
    fi

    # 3. Cài Docker Compose plugin
    if ! docker compose version &> /dev/null; then
        log_info "Cài Docker Compose plugin..."
        apt-get install -y docker-compose-plugin
        log_success "Docker Compose: $(docker compose version)"
    else
        log_success "Docker Compose đã có: $(docker compose version)"
    fi

    # 4. Cài Git
    if ! command -v git &> /dev/null; then
        log_info "Cài Git..."
        apt-get install -y git
    fi

    # 5. Tạo thư mục project
    log_info "Tạo thư mục $APP_DIR..."
    mkdir -p "$APP_DIR"

    # 6. Clone repo
    if [ ! -d "$APP_DIR/.git" ]; then
        log_info "Clone repository..."
        git clone -b "$BRANCH" "$REPO_URL" "$APP_DIR"
        log_success "Clone thành công vào $APP_DIR"
    else
        log_warn "Repo đã tồn tại, bỏ qua clone."
    fi

    # 7. Tạo file .env
    if [ ! -f "$APP_DIR/.env" ]; then
        log_warn "Chưa có file .env — copy từ .env.example..."
        cp "$APP_DIR/.env.example" "$APP_DIR/.env"
        log_warn ">>> QUAN TRỌNG: Điền đầy đủ giá trị vào $APP_DIR/.env trước khi chạy deploy!"
        log_warn ">>> nano $APP_DIR/.env"
    else
        log_success "File .env đã tồn tại."
    fi

    # 8. Tạo thư mục ssl (chứa cert nếu dùng HTTPS)
    mkdir -p "$APP_DIR/nginx/ssl"

    log_success "=== Setup hoàn tất! ==="
    log_info "Tiếp theo:"
    log_info "  1. Điền .env: nano $APP_DIR/.env"
    log_info "  2. Deploy:    bash $APP_DIR/deploy.sh deploy"
}

# =============================================================================
# COMMAND: deploy — Pull code mới và restart service
# =============================================================================
deploy() {
    log_info "=== Bắt đầu deploy StockSpace ==="

    cd "$APP_DIR"

    # Kiểm tra file .env
    if [ ! -f ".env" ]; then
        log_error "Không tìm thấy .env! Chạy 'bash deploy.sh setup' trước."
    fi

    # Kiểm tra các biến bắt buộc
    check_env

    # 1. Pull code mới
    log_info "Pull code từ branch $BRANCH..."
    git fetch origin
    git reset --hard "origin/$BRANCH"
    log_success "Code đã cập nhật."

    # 2. Apply idempotent production migrations before the new app starts.
    # Every production migration must be idempotent. Files run in lexical order.
    MIGRATION_FILES=(ops/migrations/*.sql)
    if [ -f "${MIGRATION_FILES[0]}" ]; then
        log_info "Khởi động PostgreSQL và chạy migration chatbot/RAG..."
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
        for MIGRATION_FILE in "${MIGRATION_FILES[@]}"; do
            log_info "Running migration: $MIGRATION_FILE"
            docker compose -f docker-compose.yml -f docker-compose.prod.yml \
                exec -T postgres psql \
                -v ON_ERROR_STOP=1 \
                -U "${DB_USERNAME:-postgres}" \
                -d "${DB_NAME:-stockspace}" < "$MIGRATION_FILE"
        done
        log_success "Production migrations completed."
    fi

    # 3. Build và restart với Docker Compose
    log_info "Build image và khởi động containers..."
    docker compose pull postgres nginx 2>/dev/null || true  # Pull image mới nhất từ registry
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans

    # 4. Đợi app healthy
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

    # 5. Dọn dẹp image cũ
    log_info "Dọn dẹp Docker images không dùng..."
    docker image prune -f

    log_success "=== Deploy thành công! ==="
    docker compose ps
}

# =============================================================================
# COMMAND: logs — Xem log realtime
# =============================================================================
show_logs() {
    cd "$APP_DIR"
    SERVICE=${2:-app}  # Mặc định xem log của app, truyền 'postgres'/'nginx' để xem service khác
    log_info "Xem log service: $SERVICE (Ctrl+C để thoát)"
    docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f "$SERVICE"
}

# =============================================================================
# COMMAND: restart — Restart service
# =============================================================================
restart_service() {
    cd "$APP_DIR"
    SERVICE=${2:-app}
    log_info "Restart service: $SERVICE"
    docker compose -f docker-compose.yml -f docker-compose.prod.yml restart "$SERVICE"
    log_success "Đã restart $SERVICE"
}

# =============================================================================
# COMMAND: stop — Dừng toàn bộ service
# =============================================================================
stop_all() {
    cd "$APP_DIR"
    log_warn "Dừng toàn bộ services..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml down
    log_success "Đã dừng tất cả services."
}

# =============================================================================
# COMMAND: status — Xem trạng thái
# =============================================================================
show_status() {
    cd "$APP_DIR"
    log_info "=== Trạng thái containers ==="
    docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
    echo ""
    log_info "=== Disk Usage ==="
    docker system df
}

# =============================================================================
# COMMAND: backup — Backup database
# =============================================================================
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

# =============================================================================
# Kiểm tra các biến .env bắt buộc
# =============================================================================
check_env() {
    log_info "Kiểm tra biến môi trường..."
    source .env

    REQUIRED_VARS=("DB_PASSWORD" "JWT_SECRET" "CLOUDINARY_CLOUD_NAME" "CLOUDINARY_API_KEY" "CLOUDINARY_API_SECRET" "OPENROUTER_API_KEY" "OPENROUTER_MODEL" "PUBLIC_HTTPS_READY")
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
    if [ "$PUBLIC_HTTPS_READY" != "true" ]; then
        log_error "Production phải có HTTPS trước khi deploy (PUBLIC_HTTPS_READY=true)."
    fi

    log_success "Tất cả biến bắt buộc đã có."
}

# =============================================================================
# ENTRYPOINT
# =============================================================================
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
    *)       print_usage ;;
esac
