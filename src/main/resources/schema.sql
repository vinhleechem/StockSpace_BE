-- Must run before Hibernate so vector(1536) is a known PostgreSQL type while
-- Hibernate validates or creates the system_knowledge table.
CREATE EXTENSION IF NOT EXISTS vector;
