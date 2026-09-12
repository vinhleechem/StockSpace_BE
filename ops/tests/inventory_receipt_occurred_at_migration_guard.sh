#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260912_02_add_inventory_receipt_occurred_at.sql"
PREFLIGHT="$ROOT/ops/maintenance/inventory_receipt_occurred_at_preflight.sql"
POSTCHECK="$ROOT/ops/maintenance/inventory_receipt_occurred_at_postcheck.sql"

for file in "$MIGRATION" "$PREFLIGHT" "$POSTCHECK"; do
  [[ -f "$file" ]] || { printf 'FAIL: missing receipt occurrence artifact: %s\n' "$file" >&2; exit 1; }
done

grep -Fq 'ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMP' "$MIGRATION"
grep -Fq 'COALESCE(created_at, CURRENT_TIMESTAMP)' "$MIGRATION"
grep -Fq 'ALTER COLUMN occurred_at SET DEFAULT CURRENT_TIMESTAMP' "$MIGRATION"
grep -Fq 'ALTER COLUMN occurred_at SET NOT NULL' "$MIGRATION"
grep -Fq 'null_occurred_at_rows' "$POSTCHECK"

if grep -Eiq 'DROP[[:space:]]+(TABLE|COLUMN)|TRUNCATE|DELETE[[:space:]]+FROM' "$MIGRATION"; then
  printf 'FAIL: receipt occurrence migration must be additive and non-destructive\n' >&2
  exit 1
fi

if grep -Eiq '^[[:space:]]*(BEGIN|COMMIT)[[:space:]]*;' "$MIGRATION"; then
  printf 'FAIL: migration runner owns the transaction boundary\n' >&2
  exit 1
fi

printf 'PASS: receipt occurrence migration/preflight/postcheck guard checks passed\n'
