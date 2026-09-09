#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260909_01_align_warehouse_rental_area_allocation.sql"
PREFLIGHT="$ROOT/ops/maintenance/rental_area_allocation_preflight.sql"

[[ -f "$MIGRATION" ]] || {
  printf 'FAIL: missing rental area allocation migration\n' >&2
  exit 1
}
[[ -f "$PREFLIGHT" ]] || {
  printf 'FAIL: missing rental area allocation preflight\n' >&2
  exit 1
}

grep -Fq 'SET capacity = ROUND(l.width * l.length, 2)' "$MIGRATION"
grep -Fq 'CREATE INDEX IF NOT EXISTS idx_rental_contracts_warehouse_allocation_period' "$MIGRATION"
grep -Fq 'WHERE is_active = TRUE AND is_deleted = FALSE' "$MIGRATION"
grep -Fq 'multiple active default layouts exist' "$MIGRATION"
grep -Fq 'default layout area exceeds warehouses.capacity precision' "$MIGRATION"

if grep -Eiq 'DROP[[:space:]]+(TABLE|COLUMN)|TRUNCATE|DELETE[[:space:]]+FROM[[:space:]]+public\.rental_contracts' "$MIGRATION"; then
  printf 'FAIL: rental area allocation migration must not perform destructive contract changes\n' >&2
  exit 1
fi

if grep -Eiq 'WHOLE_WAREHOUSE|PARTIAL_AREA|RENTAL_SCOPE' "$MIGRATION" "$PREFLIGHT"; then
  printf 'FAIL: rental area allocation must not introduce persisted rental-scope concepts\n' >&2
  exit 1
fi

grep -Fq 'WITH active_default_area AS' "$PREFLIGHT"
grep -Fq "status IN ('PENDING_TENANT_CONFIRM', 'ACTIVE')" "$PREFLIGHT"
grep -Fq 'End dates are inclusive' "$PREFLIGHT"

printf 'PASS: rental area allocation migration and preflight guard checks passed\n'
