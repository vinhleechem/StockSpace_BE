-- Add durable metadata/normalized-row staging for safe XLSX imports.
-- This migration is additive and never stores the uploaded binary workbook.

DO $migration$
BEGIN
    IF to_regclass('public.users') IS NULL
       OR to_regclass('public.warehouses') IS NULL
       OR to_regclass('public.inventory_audits') IS NULL THEN
        RAISE EXCEPTION
            'WMS import staging migration requires users, warehouses and inventory_audits';
    END IF;
END
$migration$;

CREATE TABLE IF NOT EXISTS public.wms_import_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID,
    audit_id UUID,
    created_by UUID NOT NULL,
    import_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    context_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    failure_message TEXT,
    applied_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wms_import_jobs_tenant
        FOREIGN KEY (tenant_id) REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_wms_import_jobs_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES public.warehouses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_wms_import_jobs_audit
        FOREIGN KEY (audit_id) REFERENCES public.inventory_audits (id) ON DELETE RESTRICT,
    CONSTRAINT fk_wms_import_jobs_created_by
        FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_wms_import_jobs_type
        CHECK (import_type IN ('SKU_CATALOG', 'OFFLINE_MOVEMENT', 'AUDIT_RECONCILIATION')),
    CONSTRAINT ck_wms_import_jobs_status
        CHECK (status IN ('VALIDATED', 'INVALID', 'APPLIED', 'FAILED')),
    CONSTRAINT ck_wms_import_jobs_scope
        CHECK (
            (import_type = 'SKU_CATALOG' AND warehouse_id IS NULL AND audit_id IS NULL)
            OR (import_type = 'OFFLINE_MOVEMENT' AND warehouse_id IS NOT NULL AND audit_id IS NULL)
            OR (import_type = 'AUDIT_RECONCILIATION' AND warehouse_id IS NOT NULL AND audit_id IS NOT NULL)
        ),
    CONSTRAINT ck_wms_import_jobs_row_counts
        CHECK (
            total_rows >= 0
            AND valid_rows >= 0
            AND invalid_rows >= 0
            AND valid_rows + invalid_rows <= total_rows
        ),
    CONSTRAINT ck_wms_import_jobs_hashes
        CHECK (file_sha256 ~ '^[0-9a-fA-F]{64}$' AND content_sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_wms_import_jobs_version
        CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS public.wms_import_rows (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    sheet_name VARCHAR(80) NOT NULL,
    row_number INTEGER NOT NULL,
    group_key VARCHAR(120),
    normalized_payload JSONB NOT NULL,
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    result_resource_type VARCHAR(40),
    result_resource_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wms_import_rows_job
        FOREIGN KEY (job_id) REFERENCES public.wms_import_jobs (id) ON DELETE CASCADE,
    CONSTRAINT ck_wms_import_rows_row_number
        CHECK (row_number > 0),
    CONSTRAINT ck_wms_import_rows_errors_array
        CHECK (jsonb_typeof(validation_errors) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_wms_import_jobs_tenant_created
    ON public.wms_import_jobs (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wms_import_jobs_created_by_created
    ON public.wms_import_jobs (created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_wms_import_jobs_status
    ON public.wms_import_jobs (status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_wms_import_jobs_applied_content
    ON public.wms_import_jobs (tenant_id, import_type, content_sha256)
    WHERE status = 'APPLIED' AND is_active = TRUE AND is_deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_wms_import_rows_job_sheet_row
    ON public.wms_import_rows (job_id, sheet_name, row_number);

CREATE INDEX IF NOT EXISTS idx_wms_import_rows_job_group
    ON public.wms_import_rows (job_id, group_key);
