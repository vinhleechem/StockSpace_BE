CREATE EXTENSION IF NOT EXISTS vector;

-- Conversation entity memory is additive and safe for existing installations.
-- Hibernate creates the column for a new schema; this also backfills dev
-- databases where chat_sessions already exists before Hibernate runs.
ALTER TABLE IF EXISTS chat_sessions
    ADD COLUMN IF NOT EXISTS context_json TEXT;
