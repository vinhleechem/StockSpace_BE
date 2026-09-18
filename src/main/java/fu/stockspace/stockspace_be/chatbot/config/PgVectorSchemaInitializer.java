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
    private static final String WAREHOUSE_HNSW_INDEX_NAME =
            "idx_warehouses_search_embedding_hnsw";

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

    private static final String WAREHOUSE_ADD_NONZERO_CONSTRAINT_SQL = """
            DO $pgvector$
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
                        )
                        NOT VALID;
                END IF;
            END
            $pgvector$;
            """;

    private static final String CREATE_WAREHOUSE_HNSW_INDEX_SQL = """
            CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_warehouses_search_embedding_hnsw
            ON public.warehouses
            USING hnsw (search_embedding vector_cosine_ops)
            WHERE search_embedding IS NOT NULL
              AND is_active = true
              AND is_deleted = false
              AND status = 'AVAILABLE'
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
                initializeWarehouseSchema(connection);
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
        ensureHnswIndex(
                connection,
                HNSW_INDEX_NAME,
                CREATE_HNSW_INDEX_SQL,
                DROP_INVALID_HNSW_INDEX_SQL
        );
        log.info("pgvector schema is ready (vector(1536), cosine HNSW index)");
    }

    private void initializeWarehouseSchema(Connection connection) throws SQLException {
        if (!tableExists(connection, "warehouses")) {
            log.info("Skipping pgvector warehouse DDL because public.warehouses does not exist");
            return;
        }
        execute(
                connection,
                "ALTER TABLE public.warehouses "
                        + "ADD COLUMN IF NOT EXISTS search_embedding vector(1536), "
                        + "ADD COLUMN IF NOT EXISTS search_embedding_str text, "
                        + "ADD COLUMN IF NOT EXISTS search_embedding_model varchar(150), "
                        + "ADD COLUMN IF NOT EXISTS search_embedding_dimensions integer, "
                        + "ADD COLUMN IF NOT EXISTS search_content_hash varchar(64)"
        );
        execute(connection, WAREHOUSE_ADD_NONZERO_CONSTRAINT_SQL);
        execute(
                connection,
                "ALTER TABLE public.warehouses "
                        + "VALIDATE CONSTRAINT ck_warehouses_search_embedding_nonzero"
        );
        ensureHnswIndex(
                connection,
                WAREHOUSE_HNSW_INDEX_NAME,
                CREATE_WAREHOUSE_HNSW_INDEX_SQL,
                "DROP INDEX CONCURRENTLY IF EXISTS public." + WAREHOUSE_HNSW_INDEX_NAME
        );
        log.info("pgvector warehouse search schema is ready (vector(1536), cosine HNSW index)");
    }

    private void ensureHnswIndex(
            Connection connection,
            String indexName,
            String createSql,
            String dropSql
    ) throws SQLException {
        Boolean indexValid = findHnswIndexValidity(connection, indexName);
        if (Boolean.FALSE.equals(indexValid)) {
            log.warn("Rebuilding invalid pgvector HNSW index {}", indexName);
            execute(connection, dropSql);
        }
        execute(connection, createSql);
    }

    private Boolean findHnswIndexValidity(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(HNSW_INDEX_VALIDITY_SQL)) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBoolean(1) : null;
            }
        }
    }

    private boolean systemKnowledgeTableExists(Connection connection) throws SQLException {
        return tableExists(connection, "system_knowledge");
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT to_regclass('public." + tableName + "') IS NOT NULL"
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
