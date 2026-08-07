-- Idempotent rollout migration for chatbot/RAG changes.
-- Safe on a fresh database: each block runs only when the legacy table exists.

CREATE EXTENSION IF NOT EXISTS vector;
-- The pinned image provides the tested extension files; existing volumes may
-- still record an older installed extension version.
ALTER EXTENSION vector UPDATE;

DO $migration$
BEGIN
    IF to_regclass('public.chat_sessions') IS NOT NULL THEN
        ALTER TABLE chat_sessions
            ADD COLUMN IF NOT EXISTS expires_at timestamp without time zone;
        ALTER TABLE chat_sessions
            ADD COLUMN IF NOT EXISTS version bigint;
        UPDATE chat_sessions SET version = 0 WHERE version IS NULL;
        ALTER TABLE chat_sessions ALTER COLUMN version SET DEFAULT 0;
        ALTER TABLE chat_sessions ALTER COLUMN version SET NOT NULL;
        ALTER TABLE chat_sessions
            ALTER COLUMN session_token TYPE varchar(64);
        CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id
            ON chat_sessions (user_id);
        CREATE INDEX IF NOT EXISTS idx_chat_sessions_session_token
            ON chat_sessions (session_token);
        CREATE INDEX IF NOT EXISTS idx_chat_sessions_expires_at
            ON chat_sessions (expires_at);
    END IF;

    IF to_regclass('public.chat_messages') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created
            ON chat_messages (session_id, created_at);
    END IF;

    IF to_regclass('public.system_knowledge') IS NOT NULL THEN
        ALTER TABLE public.system_knowledge
            ADD COLUMN IF NOT EXISTS source_id varchar(100);
        ALTER TABLE public.system_knowledge
            ADD COLUMN IF NOT EXISTS embedding_model varchar(150);
        ALTER TABLE public.system_knowledge
            ADD COLUMN IF NOT EXISTS embedding_dimensions integer;
        ALTER TABLE public.system_knowledge
            ADD COLUMN IF NOT EXISTS content_hash varchar(64);
        ALTER TABLE public.system_knowledge
            ADD COLUMN IF NOT EXISTS embedding_vector vector(1536);

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'ck_system_knowledge_embedding_vector_nonzero'
              AND conrelid = 'public.system_knowledge'::regclass
        ) THEN
            ALTER TABLE public.system_knowledge
                ADD CONSTRAINT ck_system_knowledge_embedding_vector_nonzero
                CHECK (
                    embedding_vector IS NULL
                    OR vector_norm(embedding_vector) > 0
                )
                NOT VALID;
        END IF;
        ALTER TABLE public.system_knowledge
            VALIDATE CONSTRAINT ck_system_knowledge_embedding_vector_nonzero;

        CREATE UNIQUE INDEX IF NOT EXISTS idx_system_knowledge_source_id
            ON public.system_knowledge (source_id)
            WHERE source_id IS NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_system_knowledge_category
            ON public.system_knowledge (category);
        CREATE INDEX IF NOT EXISTS idx_system_knowledge_embedding_hnsw
            ON public.system_knowledge
            USING hnsw (embedding_vector vector_cosine_ops)
            WHERE embedding_vector IS NOT NULL
              AND is_active = true
              AND is_deleted = false;
    END IF;
END
$migration$;
