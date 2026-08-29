#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "${RUN_MIGRATION_DB_TESTS:-false}" != "true" ]]; then
  printf 'SKIP: set RUN_MIGRATION_DB_TESTS=true to run the disposable DB test\n'
  exit 0
fi

if [[ "${PGDATABASE:-}" != *test* ]]; then
  printf 'Refusing integration test: PGDATABASE must contain "test"\n' >&2
  exit 2
fi

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT/ops/run-migrations.sh"
TEST_ROOT="$(mktemp -d)"
TEST_MIGRATIONS="$TEST_ROOT/migrations"
trap 'rm -rf "$TEST_ROOT"' EXIT
mkdir -p "$TEST_MIGRATIONS"

TABLE_NAME="migration_runner_probe_$(date +%s)_$$"
FIRST_FILE="001_${TABLE_NAME}.sql"
SECOND_FILE="002_${TABLE_NAME}_column.sql"
FAIL_FILE="003_${TABLE_NAME}_failure.sql"

cleanup() {
  psql -X -v ON_ERROR_STOP=1 -U "${PGUSER:-postgres}" -d "$PGDATABASE" \
    -c "DROP TABLE IF EXISTS public.$TABLE_NAME; DELETE FROM public.schema_migrations WHERE filename IN ('$FIRST_FILE', '$SECOND_FILE', '$FAIL_FILE');" \
    >/dev/null
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

printf 'CREATE TABLE public.%s (id integer PRIMARY KEY);\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$FIRST_FILE"
printf 'ALTER TABLE public.%s ADD COLUMN marker text;\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$SECOND_FILE"

bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/first.log"
grep -Fq "Applying: $FIRST_FILE" "$TEST_ROOT/first.log"
grep -Fq "Applying: $SECOND_FILE" "$TEST_ROOT/first.log"

bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/second.log"
grep -Fq "Already applied: $FIRST_FILE" "$TEST_ROOT/second.log"
grep -Fq "Already applied: $SECOND_FILE" "$TEST_ROOT/second.log"
if grep -Fq "Applying:" "$TEST_ROOT/second.log"; then
  printf 'FAIL: second run applied a migration\n' >&2
  exit 1
fi

printf 'ALTER TABLE public.%s ADD COLUMN changed text;\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$SECOND_FILE"
if bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/checksum.log" 2>&1; then
  printf 'FAIL: checksum mismatch was accepted\n' >&2
  exit 1
fi
grep -Fq "checksum mismatch for $SECOND_FILE" "$TEST_ROOT/checksum.log"

printf 'ALTER TABLE public.%s ADD COLUMN marker text;\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$SECOND_FILE"
printf 'ALTER TABLE public.%s ADD COLUMN failed text;\nSELECT 1 / 0;\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$FAIL_FILE"
if bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/failure.log" 2>&1; then
  printf 'FAIL: invalid SQL was accepted\n' >&2
  exit 1
fi

if psql -X -At -v ON_ERROR_STOP=1 -U "${PGUSER:-postgres}" -d "$PGDATABASE" \
    -c "SELECT COUNT(*) FROM public.schema_migrations WHERE filename = '$FAIL_FILE';" | grep -vq '^0$'; then
  printf 'FAIL: failed migration was recorded in the ledger\n' >&2
  exit 1
fi
rm -f "$TEST_MIGRATIONS/$FAIL_FILE"

CONCURRENT_FILE="004_${TABLE_NAME}_concurrent.sql"
printf 'SELECT pg_sleep(1); ALTER TABLE public.%s ADD COLUMN concurrent_marker text;\n' "$TABLE_NAME" > "$TEST_MIGRATIONS/$CONCURRENT_FILE"
bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/concurrent-a.log" 2>&1 &
first_pid=$!
bash "$RUNNER" --migrations-dir "$TEST_MIGRATIONS" > "$TEST_ROOT/concurrent-b.log" 2>&1 &
second_pid=$!
wait "$first_pid"
wait "$second_pid"

if [[ "$(psql -X -At -v ON_ERROR_STOP=1 -U "${PGUSER:-postgres}" -d "$PGDATABASE" -c "SELECT COUNT(*) FROM public.schema_migrations WHERE filename = '$CONCURRENT_FILE';")" != "1" ]]; then
  printf 'FAIL: concurrent runners did not record exactly one migration\n' >&2
  exit 1
fi

printf 'PASS: migration runner first-run, no-op replay, checksum, failure and concurrency checks passed\n'
