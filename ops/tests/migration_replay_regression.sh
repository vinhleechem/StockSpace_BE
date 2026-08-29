#!/usr/bin/env bash

set -Eeuo pipefail

# Regression fixture for the old deploy behavior: every SQL file was replayed
# on every deployment, with no applied-migration ledger.

TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

MIGRATIONS_DIR="$TEST_ROOT/migrations"
BIN_DIR="$TEST_ROOT/bin"
STATE_FILE="$TEST_ROOT/applied-files"
mkdir -p "$MIGRATIONS_DIR" "$BIN_DIR"

printf '%s\n' '-- fixture: first migration' > "$MIGRATIONS_DIR/001_create_table.sql"
printf '%s\n' '-- fixture: second migration' > "$MIGRATIONS_DIR/002_add_column.sql"

cat > "$BIN_DIR/psql" <<'FAKE_PSQL'
#!/usr/bin/env bash
set -Eeuo pipefail

state_file="${MIGRATION_TEST_STATE:?MIGRATION_TEST_STATE is required}"
marker="$(sed -n 's/^-- fixture: //p' | head -n 1)"

if grep -Fqx "$marker" "$state_file" 2>/dev/null; then
  printf 'fixture psql: migration already applied: %s\n' "$marker" >&2
  exit 3
fi

printf '%s\n' "$marker" >> "$state_file"
FAKE_PSQL
chmod +x "$BIN_DIR/psql"

run_legacy_deploy_loop() {
  local migration_file
  for migration_file in "$MIGRATIONS_DIR"/*.sql; do
    psql < "$migration_file"
  done
}

export MIGRATION_TEST_STATE="$STATE_FILE"
export PATH="$BIN_DIR:$PATH"

run_legacy_deploy_loop

if run_legacy_deploy_loop; then
  printf 'FAIL: legacy migration loop unexpectedly succeeded on replay\n' >&2
  exit 1
fi

printf 'PASS: legacy migration loop fails when an already-applied SQL file is replayed\n'
