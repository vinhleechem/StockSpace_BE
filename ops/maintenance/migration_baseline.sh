#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MANIFEST="$SCRIPT_DIR/migration_baseline_manifest.txt"
PREFLIGHT="$SCRIPT_DIR/migration_baseline_preflight.sql"
MODE="local"
APPLY="false"

usage() {
  cat <<'USAGE'
Usage: ops/maintenance/migration_baseline.sh [--apply] [--docker]

Default mode is dry-run. It validates the manifest and prints files that would
be recorded but does not connect to or write the database. --apply is required
for the explicit baseline operation. --docker uses the project postgres
container; local mode uses normal PostgreSQL environment variables.
USAGE
}

while (($# > 0)); do
  case "$1" in
    --apply) APPLY="true"; shift ;;
    --docker) MODE="docker"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ ! -f "$MANIFEST" || ! -f "$PREFLIGHT" ]]; then
  printf 'Baseline manifest or preflight SQL is missing\n' >&2
  exit 2
fi

mapfile -t BASELINE_FILES < <(
  sed -e 's/\r$//' -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$MANIFEST"
)
if ((${#BASELINE_FILES[@]} == 0)); then
  printf 'Baseline manifest is empty\n' >&2
  exit 2
fi

for filename in "${BASELINE_FILES[@]}"; do
  if [[ "$filename" == 20260825_* ]]; then
    printf 'Refusing to baseline post-refactor migration: %s\n' "$filename" >&2
    exit 3
  fi
  if [[ ! -f "$REPO_ROOT/ops/migrations/$filename" ]]; then
    printf 'Manifest file does not exist: %s\n' "$filename" >&2
    exit 3
  fi
done

run_psql_file() {
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

printf 'Baseline manifest validated (%d historical files).\n' "${#BASELINE_FILES[@]}"
printf '%s\n' "Preflight: $PREFLIGHT"

if [[ "$APPLY" != "true" ]]; then
  printf 'DRY-RUN: no database connection or write was performed.\n'
  printf 'Would baseline:\n'
  printf '  - %s\n' "${BASELINE_FILES[@]}"
  exit 0
fi

if [[ "${ALLOW_MIGRATION_BASELINE:-}" != "true" ]]; then
  printf 'Refusing baseline write: set ALLOW_MIGRATION_BASELINE=true explicitly\n' >&2
  exit 4
fi

run_psql_file < "$PREFLIGHT"

build_baseline_sql() {
  printf '%s\n' "SELECT pg_advisory_lock(hashtextextended('stockspace.schema_migrations', 0));"
  printf '%s\n' 'CREATE TABLE IF NOT EXISTS public.schema_migrations ('
  printf '%s\n' '    filename text PRIMARY KEY,'
  printf '%s\n' '    checksum char(64) NOT NULL,'
  printf '%s\n' '    applied_at timestamptz NOT NULL DEFAULT now()'
  printf '%s\n' ');'
  printf '%s\n' 'BEGIN;'

  local filename checksum quoted_filename quoted_checksum
  for filename in "${BASELINE_FILES[@]}"; do
    checksum="$(sha256sum "$REPO_ROOT/ops/migrations/$filename" | awk '{print $1}')"
    quoted_filename="${filename//\'/\'\'}"
    quoted_checksum="${checksum//\'/\'\'}"
    printf '%s\n' 'DO $baseline$ BEGIN'
    printf '%s\n' "  IF EXISTS (SELECT 1 FROM public.schema_migrations WHERE filename = '$quoted_filename' AND checksum <> '$quoted_checksum') THEN"
    printf '%s\n' "    RAISE EXCEPTION 'Baseline checksum mismatch for $quoted_filename';"
    printf '%s\n' '  END IF;'
    printf '%s\n' 'END'
    printf '%s\n' '$baseline$;'
    printf '%s\n' "INSERT INTO public.schema_migrations (filename, checksum) VALUES ('$quoted_filename', '$quoted_checksum') ON CONFLICT (filename) DO NOTHING;"
  done

  printf '%s\n' 'COMMIT;'
  printf '%s\n' "SELECT pg_advisory_unlock(hashtextextended('stockspace.schema_migrations', 0));"
}

build_baseline_sql | run_psql_file
printf 'Baseline applied successfully. No post-refactor migration was marked.\n'
