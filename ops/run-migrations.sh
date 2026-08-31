#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
MIGRATIONS_DIR="$REPO_ROOT/ops/migrations"
MODE="local"
DRY_RUN="false"

usage() {
  cat <<'USAGE'
Usage: ops/run-migrations.sh [options]

Options:
  --dry-run                   List pending migrations without applying them.
  --docker                    Execute psql through the project postgres container.
  --migrations-dir <path>    Override the SQL migration directory.
  -h, --help                 Show this help.

Local mode reads the normal PostgreSQL environment variables (PGHOST, PGPORT,
PGUSER, PGDATABASE, PGPASSWORD, or project DB_* variables). Docker mode uses
docker compose and the postgres service from the project compose files.
USAGE
}

while (($# > 0)); do
  case "$1" in
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    --docker)
      MODE="docker"
      shift
      ;;
    --migrations-dir)
      if (($# < 2)); then
        printf '%s\n' "--migrations-dir requires a path" >&2
        exit 2
      fi
      MIGRATIONS_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$MIGRATIONS_DIR" != /* ]]; then
  MIGRATIONS_DIR="$(cd -- "$MIGRATIONS_DIR" 2>/dev/null && pwd)" || {
    printf 'Migration directory does not exist: %s\n' "$MIGRATIONS_DIR" >&2
    exit 2
  }
fi

mapfile -t MIGRATION_FILES < <(find "$MIGRATIONS_DIR" -maxdepth 1 -type f -name '*.sql' -print | sort)

if ((${#MIGRATION_FILES[@]} == 0)); then
  printf 'No SQL migrations found in %s\n' "$MIGRATIONS_DIR"
  exit 0
fi

sql_quote() {
  local value="$1"
  value="${value//\'/\'\'}"
  printf "'%s'" "$value"
}

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

run_psql_stream() {
  if [[ "$MODE" == "docker" ]]; then
    docker compose \
      -f "$REPO_ROOT/docker-compose.yml" \
      -f "$REPO_ROOT/docker-compose.prod.yml" \
      exec -T postgres psql \
      -X -v ON_ERROR_STOP=1 \
      -U "${DB_USERNAME:-postgres}" \
      -d "${DB_NAME:-stockspace}"
  else
    PSQL_USER="${PGUSER:-${DB_USERNAME:-postgres}}"
    PSQL_DATABASE="${PGDATABASE:-${DB_NAME:-stockspace}}"
    psql -X -v ON_ERROR_STOP=1 -U "$PSQL_USER" -d "$PSQL_DATABASE"
  fi
}

build_sql_stream() {
  printf '%s\n' "SELECT pg_advisory_lock(hashtextextended('stockspace.schema_migrations', 0));"

  if [[ "$DRY_RUN" == "true" ]]; then
    printf '%s\n' "SELECT to_regclass('public.schema_migrations') IS NOT NULL AS ledger_exists;"
    printf '%s\n' '\gset migration_ledger_'
    printf '%s\n' '\if :migration_ledger_ledger_exists'
  else
    printf '%s\n' 'CREATE TABLE IF NOT EXISTS public.schema_migrations ('
    printf '%s\n' '    filename text PRIMARY KEY,'
    printf '%s\n' '    checksum char(64) NOT NULL,'
    printf '%s\n' '    applied_at timestamptz NOT NULL DEFAULT now()'
    printf '%s\n' ');'
    printf '%s\n' 'CREATE INDEX IF NOT EXISTS idx_schema_migrations_applied_at ON public.schema_migrations (applied_at);'
  fi

  local migration_file filename checksum quoted_filename quoted_checksum
  for migration_file in "${MIGRATION_FILES[@]}"; do
    filename="$(basename -- "$migration_file")"
    checksum="$(sha256_file "$migration_file")"
    quoted_filename="$(sql_quote "$filename")"
    quoted_checksum="$(sql_quote "$checksum")"

    printf '%s\n' "SELECT EXISTS (SELECT 1 FROM public.schema_migrations WHERE filename = $quoted_filename) AS applied,"
    printf '%s\n' "       COALESCE((SELECT checksum = $quoted_checksum FROM public.schema_migrations WHERE filename = $quoted_filename), false) AS checksum_ok;"
    printf '%s\n' '\gset migration_'

    printf '%s\n' '\if :migration_applied'
    printf '%s\n' '\if :migration_checksum_ok'
    printf '%s%s\n' '    \echo Already applied: ' "$filename"
    printf '%s\n' '  \else'
    printf '%s%s%s\n' '    \echo ERROR: checksum mismatch for ' "$filename" ' >&2'
    printf '%s\n' '    SELECT 1 / 0; -- force ON_ERROR_STOP after checksum mismatch'
    printf '%s\n' '  \endif'
    printf '%s\n' '\else'

    if [[ "$DRY_RUN" == "true" ]]; then
      printf '%s%s\n' '  \echo PENDING: ' "$filename"
    else
      printf '%s%s\n' '  \echo Applying: ' "$filename"
      printf '%s\n' '  BEGIN;'
      sed 's/^/  /' "$migration_file"
      printf '%s\n' "  INSERT INTO public.schema_migrations (filename, checksum) VALUES ($quoted_filename, $quoted_checksum);"
      printf '%s\n' '  COMMIT;'
    fi

    printf '%s\n' '\endif'
  done

  if [[ "$DRY_RUN" == "true" ]]; then
    printf '%s\n' '\else'
    printf '%s\n' '\echo ERROR: schema_migrations does not exist; initialize or baseline it before dry-run >&2'
    printf '%s\n' 'SELECT 1 / 0; -- force ON_ERROR_STOP when the ledger is missing'
    printf '%s\n' '\endif'
  fi

  printf '%s\n' "SELECT pg_advisory_unlock(hashtextextended('stockspace.schema_migrations', 0));"
}

build_sql_stream | run_psql_stream
printf 'Migration runner completed successfully (%s mode%s).\n' "$MODE" "$([[ "$DRY_RUN" == "true" ]] && printf ', dry-run' || true)"
