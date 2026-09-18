-- Semantic search index for public warehouse profiles.
-- Eligibility and tenant authorization remain in the application queries;
-- this vector is only a relevance signal.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE public.warehouses
    ADD COLUMN IF NOT EXISTS search_embedding vector(1536),
    ADD COLUMN IF NOT EXISTS search_embedding_str text,
    ADD COLUMN IF NOT EXISTS search_embedding_model varchar(150),
    ADD COLUMN IF NOT EXISTS search_embedding_dimensions integer,
    ADD COLUMN IF NOT EXISTS search_content_hash varchar(64);

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_warehouses_search_embedding_nonzero'
          AND conrelid = 'public.warehouses'::regclass
    ) THEN
        ALTER TABLE public.warehouses
            ADD CONSTRAINT ck_warehouses_search_embedding_nonzero
            CHECK (
                search_embedding IS NULL
                OR vector_norm(search_embedding) > 0
            ) NOT VALID;
    END IF;
END
$migration$;

ALTER TABLE public.warehouses
    VALIDATE CONSTRAINT ck_warehouses_search_embedding_nonzero;

CREATE INDEX IF NOT EXISTS idx_warehouses_search_embedding_hnsw
    ON public.warehouses
    USING hnsw (search_embedding vector_cosine_ops)
    WHERE search_embedding IS NOT NULL
      AND is_active = true
      AND is_deleted = false
      AND status = 'AVAILABLE';
