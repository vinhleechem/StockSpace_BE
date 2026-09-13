#!/usr/bin/env bash

set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRATION="$ROOT/ops/migrations/20260912_01_add_wms_import_jobs.sql"
PREFLIGHT="$ROOT/ops/maintenance/wms_import_jobs_preflight.sql"
POSTCHECK="$ROOT/ops/maintenance/wms_import_jobs_postcheck.sql"

for file in "$MIGRATION" "$PREFLIGHT" "$POSTCHECK"; do
  [[ -f "$file" ]] || {
    printf 'FAIL: missing WMS import migration artifact: %s\n' "$file" >&2
    exit 1
  }
done

grep -Fq 'CREATE TABLE IF NOT EXISTS public.wms_import_jobs' "$MIGRATION"
grep -Fq 'CREATE TABLE IF NOT EXISTS public.wms_import_rows' "$MIGRATION"
grep -Fq 'content_sha256 CHAR(64)' "$MIGRATION"
grep -Fq 'context_metadata JSONB' "$MIGRATION"
grep -Fq 'ux_wms_import_jobs_applied_content' "$MIGRATION"
grep -Fq 'ON DELETE CASCADE' "$MIGRATION"
grep -Fq 'missing_required_table' "$PREFLIGHT"
grep -Fq 'orphan_import_rows' "$POSTCHECK"

if grep -Eiq 'DROP[[:space:]]+(TABLE|COLUMN)|TRUNCATE|DELETE[[:space:]]+FROM' "$MIGRATION"; then
  printf 'FAIL: WMS import migration must be additive and non-destructive\n' >&2
  exit 1
fi

if grep -Eiq '^[[:space:]]*(BEGIN|COMMIT)[[:space:]]*;' "$MIGRATION"; then
  printf 'FAIL: migration runner owns the transaction boundary\n' >&2
  exit 1
fi

printf 'PASS: WMS import migration/preflight/postcheck guard checks passed\n'
