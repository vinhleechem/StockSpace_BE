#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260831_01_refactor_stock_batches_for_fifo_picking.sql"
PREFLIGHT="$ROOT/ops/maintenance/outbound_fifo_picking_preflight.sql"
POSTCHECK="$ROOT/ops/maintenance/outbound_fifo_picking_postcheck.sql"
LEGACY="$ROOT/ops/migrations/20260815_add_stock_batch_location_unique_index.sql"

for file in "$MIGRATION" "$PREFLIGHT" "$POSTCHECK" "$LEGACY"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing expected file: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'ADD COLUMN IF NOT EXISTS stock_batch_id UUID' "$MIGRATION"
grep -Fq 'ADD COLUMN IF NOT EXISTS pick_sequence INTEGER' "$MIGRATION"
grep -Fq 'fk_inventory_receipt_items_stock_batch' "$MIGRATION"
grep -Fq 'DROP INDEX public.ux_stock_batches_sku_location_active' "$MIGRATION"
grep -Fq 'arrival_date ASC NULLS LAST' "$MIGRATION"
grep -Fq 'Positive stock without a reliable arrival timestamp' "$PREFLIGHT"
grep -Fq 'legacy_single_batch_unique_index_present' "$POSTCHECK"

if grep -Eiq 'CREATE[[:space:]]+TABLE.*(stock_lot|pick_list)' "$MIGRATION"; then
  printf 'FAIL: FP01 must not create a second lot or pick-list table\n' >&2
  exit 1
fi

if ! git diff --quiet -- "$LEGACY"; then
  printf 'FAIL: the previously deployed unique-index migration was modified\n' >&2
  exit 1
fi

printf 'PASS: FIFO picking migration files and legacy-migration guard checks passed\n'
