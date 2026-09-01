#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260901_01_add_warehouse_rack_shelf_count.sql"
LEGACY="$ROOT/ops/migrations/20260815_migrate_layout_geometry_to_meters.sql"

for file in "$MIGRATION" "$LEGACY"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing expected file: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'ADD COLUMN IF NOT EXISTS shelf_count INTEGER' "$MIGRATION"
grep -Fq 'ALTER COLUMN shelf_count SET DEFAULT 1' "$MIGRATION"
grep -Fq 'ALTER COLUMN shelf_count SET NOT NULL' "$MIGRATION"
grep -Fq 'ck_warehouse_racks_positive_shelf_count' "$MIGRATION"
grep -Fq 'ck_warehouse_bins_positive_shelf_level' "$MIGRATION"
grep -Fq 'MAX(b.shelf_level)' "$MIGRATION"
grep -Fq 'b.shelf_level > r.shelf_count' "$MIGRATION"

if grep -Eiq 'DROP[[:space:]]+(TABLE|COLUMN)' "$MIGRATION"; then
  printf 'FAIL: rack shelf count migration must be additive\n' >&2
  exit 1
fi

if ! git diff --quiet -- "$LEGACY"; then
  printf 'FAIL: previously deployed layout migration was modified\n' >&2
  exit 1
fi

printf 'PASS: rack shelf count migration guard checks passed\n'
