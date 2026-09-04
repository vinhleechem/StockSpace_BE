#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260904_01_add_warehouse_draft_status.sql"
LEGACY="$ROOT/ops/migrations/20260825_05_remove_rented_warehouse_status.sql"

for file in "$MIGRATION" "$LEGACY"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing expected file: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'DROP CONSTRAINT IF EXISTS warehouses_status_check' "$MIGRATION"
grep -Fq "CHECK (status IN ('DRAFT', 'AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE'))" "$MIGRATION"

if ! git diff --quiet -- "$LEGACY"; then
  printf 'FAIL: previously deployed warehouse-status migration was modified\n' >&2
  exit 1
fi

printf 'PASS: warehouse DRAFT status migration guard checks passed\n'
