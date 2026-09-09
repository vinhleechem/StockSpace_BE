#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260910_01_add_rental_contract_renewal_lifecycle.sql"
PREFLIGHT="$ROOT/ops/maintenance/rental_contract_renewal_preflight.sql"
POSTCHECK="$ROOT/ops/maintenance/rental_contract_renewal_postcheck.sql"

for file in "$MIGRATION" "$PREFLIGHT" "$POSTCHECK"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing renewal migration artifact: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'ADD COLUMN renewed_from_contract_id UUID' "$MIGRATION"
grep -Fq 'fk_rental_contracts_renewed_from' "$MIGRATION"
grep -Fq 'ck_rental_contracts_not_self_renewal' "$MIGRATION"
grep -Fq "'SCHEDULED'" "$MIGRATION"
grep -Fq 'uq_rental_contracts_blocking_renewal_source' "$MIGRATION"
grep -Fq "'SCHEDULED', 'ACTIVE'" "$MIGRATION"
grep -Fq 'duplicate_blocking_successor' "$PREFLIGHT"
grep -Fq 'orphan_or_self_renewal' "$POSTCHECK"
grep -Fq 'to_jsonb(c)' "$PREFLIGHT"
grep -Fq "CASE WHEN EXISTS" "$POSTCHECK"

if grep -Eiq 'DROP[[:space:]]+(TABLE|COLUMN)|TRUNCATE|DELETE[[:space:]]+FROM' "$MIGRATION"; then
  printf 'FAIL: renewal migration must be additive and non-destructive\n' >&2
  exit 1
fi

if grep -Eiq '^[[:space:]]*(BEGIN|COMMIT)[[:space:]]*;' "$MIGRATION"; then
  printf 'FAIL: migration runner owns the transaction boundary\n' >&2
  exit 1
fi

printf 'PASS: contract renewal migration/preflight/postcheck guard checks passed\n'
