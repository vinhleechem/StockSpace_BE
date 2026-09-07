#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260907_03_align_listing_publication_constraints.sql"
LEGACY="$ROOT/ops/migrations/20260828_01_refactor_listing_publication_approval.sql"

for file in "$MIGRATION" "$LEGACY"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing expected file: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'DROP CONSTRAINT IF EXISTS listing_orders_status_check' "$MIGRATION"
grep -Fq 'DROP CONSTRAINT IF EXISTS listing_orders_period_check' "$MIGRATION"

for status in PAID REFUNDED TERMINATED PENDING_APPROVAL ACTIVATED; do
  grep -Fq "'$status'" "$MIGRATION"
done

grep -Fq "status IN ('PAID', 'ACTIVATED', 'TERMINATED')" "$MIGRATION"
grep -Fq "status = 'PENDING_APPROVAL'" "$MIGRATION"
grep -Fq "status = 'REFUNDED'" "$MIGRATION"

if ! git diff --quiet -- "$LEGACY"; then
  printf 'FAIL: previously deployed publication migration was modified\n' >&2
  exit 1
fi

printf 'PASS: listing publication constraint migration guard checks passed\n'
