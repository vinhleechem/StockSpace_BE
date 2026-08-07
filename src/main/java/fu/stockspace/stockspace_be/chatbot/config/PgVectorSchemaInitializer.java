package fu.stockspace.stockspace_be.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Completes pgvector DDL after Hibernate has created a fresh schema.
 *
 * <p>The production migration runs before the application and therefore cannot
 * alter {@code system_knowledge} when Hibernate has not created it yet. This
 * runner closes that fresh-database gap. A session-level PostgreSQL advisory
 * lock serializes startup across replicas, while every DDL statement remains
 * additive and safe to retry after a partial startup.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("entityManagerFactory")
@ConditionalOnProperty(
        name = "app.chatbot.rag.pgvector.schema-initializer-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PgVectorSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSchemaInitializer.class);
    private static final int ADVISORY_LOCK_NAMESPACE = 20_260_728;
    private static final int ADVISORY_LOCK_KEY = 1_536;
    private static final int ADVISORY_LOCK_TIMEOUT_SECONDS = 60;
    private static final String HNSW_INDEX_NAME = "idx_system_knowledge_embedding_hnsw";

    private static final String ADD_NONZERO_CONSTRAINT_SQL = """
            DO $pgvector$
            BEGIN
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
            END
            $pgvector$;
            """;

    private static final String CREATE_HNSW_INDEX_SQL = """
            CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_system_knowledge_embedding_hnsw
            ON public.system_knowledge
            USING hnsw (embedding_vector vector_cosine_ops)
            WHERE embedding_vector IS NOT NULL
              AND is_active = true
              AND is_deleted = false
            """;

    private static final String HNSW_INDEX_VALIDITY_SQL = """
            SELECT index_state.indisvalid
            FROM pg_catalog.pg_index index_state
            JOIN pg_catalog.pg_class index_class
              ON index_class.oid = index_state.indexrelid
            JOIN pg_catalog.pg_namespace index_namespace
              ON index_namespace.oid = index_class.relnamespace
            WHERE index_namespace.nspname = 'public'
              AND index_class.relname = ?
            """;

    private static final String DROP_INVALID_HNSW_INDEX_SQL =
            "DROP INDEX CONCURRENTLY IF EXISTS public.idx_system_knowledge_embedding_hnsw";

    private final DataSource dataSource;

    public PgVectorSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            requirePostgreSql(connection);
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            boolean lockAcquired = false;
            try {
                acquireAdvisoryLock(connection);
                lockAcquired = true;
                initializeSchema(connection);
            } finally {
                try {
                    if (lockAcquired) {
                        releaseAdvisoryLock(connection);
                    }
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        execute(connection, "CREATE EXTENSION IF NOT EXISTS vector");
        if (!systemKnowledgeTableExists(connection)) {
            log.info("Skipping pgvector table DDL because public.system_knowledge does not exist");
            return;
        }

        execute(
                connection,
                "ALTER TABLE public.system_knowledge "
                        + "ADD COLUMN IF NOT EXISTS embedding_vector vector(1536)"
        );
        execute(connection, ADD_NONZERO_CONSTRAINT_SQL);
        execute(
                connection,
                "ALTER TABLE public.system_knowledge "
                        + "VALIDATE CONSTRAINT ck_system_knowledge_embedding_vector_nonzero"
        );
        ensureHnswIndex(connection);
        log.info("pgvector schema is ready (vector(1536), cosine HNSW index)");
    }

    private void ensureHnswIndex(Connection connection) throws SQLException {
        Boolean indexValid = findHnswIndexValidity(connection);
        if (Boolean.FALSE.equals(indexValid)) {
            log.warn("Rebuilding invalid pgvector HNSW index");
            execute(connection, DROP_INVALID_HNSW_INDEX_SQL);
        }
        execute(connection, CREATE_HNSW_INDEX_SQL);
    }

    private Boolean findHnswIndexValidity(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(HNSW_INDEX_VALIDITY_SQL)) {
            statement.setString(1, HNSW_INDEX_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBoolean(1) : null;
            }
        }
    }

    private boolean systemKnowledgeTableExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT to_regclass('public.system_knowledge') IS NOT NULL"
             )) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private void acquireAdvisoryLock(Connection connection) throws SQLException {
        executeLockFunction(
                connection,
                "SELECT pg_advisory_lock(?, ?)",
                ADVISORY_LOCK_TIMEOUT_SECONDS
        );
    }

    private void releaseAdvisoryLock(Connection connection) throws SQLException {
        executeLockFunction(connection, "SELECT pg_advisory_unlock(?, ?)", 0);
    }

    private void executeLockFunction(
            Connection connection,
            String sql,
            int timeoutSeconds
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ADVISORY_LOCK_NAMESPACE);
            statement.setInt(2, ADVISORY_LOCK_KEY);
            if (timeoutSeconds > 0) {
                statement.setQueryTimeout(timeoutSeconds);
            }
            statement.execute();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void requirePostgreSql(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equalsIgnoreCase(databaseProduct)) {
            throw new IllegalStateException(
                    "pgvector schema initialization requires PostgreSQL, found " + databaseProduct
            );
        }
    }
}
