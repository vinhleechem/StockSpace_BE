#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile certbot \
  run --rm certbot renew --quiet
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T nginx nginx -s reload
