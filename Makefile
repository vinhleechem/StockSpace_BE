
PROD_FILES := -f docker-compose.yml -f docker-compose.prod.yml

.PHONY: help \
        dev-up dev-down dev-build dev-logs dev-logs-db dev-restart \
        prod-up prod-down prod-build prod-logs prod-logs-db prod-logs-nginx prod-restart \
        status backup shell-app shell-db

help:
	@echo ""
	@echo "  StockSpace — Available commands"
	@echo ""
	@echo "  ── DEV LOCAL (docker compose up tự load override) ───────────────"
	@echo "  make dev-up         Khởi động dev (app + postgres)"
	@echo "  make dev-down       Dừng dev"
	@echo "  make dev-build      Rebuild image dev"
	@echo "  make dev-logs       Log realtime app (dev)"
	@echo "  make dev-logs-db    Log realtime postgres (dev)"
	@echo "  make dev-restart    Restart app (dev)"
	@echo ""
	@echo "  ── PRODUCTION ───────────────────────────────────────────────────"
	@echo "  make prod-up        Khởi động prod (app + postgres + nginx)"
	@echo "  make prod-down      Dừng prod"
	@echo "  make prod-build     Rebuild image prod"
	@echo "  make prod-logs      Log realtime app (prod)"
	@echo "  make prod-logs-db   Log realtime postgres (prod)"
	@echo "  make prod-logs-nginx Log realtime nginx (prod)"
	@echo "  make prod-restart   Restart app (prod)"
	@echo ""
	@echo "  ── COMMON ───────────────────────────────────────────────────────"
	@echo "  make status         Trạng thái containers"
	@echo "  make backup         Backup database"
	@echo "  make shell-app      Shell vào container app"
	@echo "  make shell-db       Shell vào postgres"
	@echo ""

dev-up:
	docker compose up -d

dev-down:
	docker compose down

dev-build:
	docker compose build --no-cache app

dev-logs:
	docker compose logs -f app

dev-logs-db:
	docker compose logs -f postgres

dev-restart:
	docker compose restart app

prod-up:
	docker compose $(PROD_FILES) up -d

prod-down:
	docker compose $(PROD_FILES) down

prod-build:
	docker compose $(PROD_FILES) build --no-cache app

prod-logs:
	docker compose $(PROD_FILES) logs -f app

prod-logs-db:
	docker compose $(PROD_FILES) logs -f postgres

prod-logs-nginx:
	docker compose $(PROD_FILES) logs -f nginx

prod-restart:
	docker compose $(PROD_FILES) restart app

status:
	@echo "── DEV ──────────────────────────────────────────"
	docker compose ps 2>/dev/null || true
	@echo "── PROD ─────────────────────────────────────────"
	docker compose $(PROD_FILES) ps 2>/dev/null || true
	@echo "── Disk ─────────────────────────────────────────"
	docker system df

backup:
	bash deploy.sh backup

shell-app:
	docker compose exec app sh

shell-db:
	docker compose exec postgres psql -U $${DB_USERNAME:-postgres} $${DB_NAME:-stockspace}
