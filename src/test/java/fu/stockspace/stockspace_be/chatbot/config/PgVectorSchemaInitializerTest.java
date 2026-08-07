package fu.stockspace.stockspace_be.chatbot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorSchemaInitializerTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData databaseMetaData;
    @Mock
    private Statement statement;
    @Mock
    private PreparedStatement advisoryLockStatement;
    @Mock
    private PreparedStatement advisoryUnlockStatement;
    @Mock
    private PreparedStatement indexValidityStatement;
    @Mock
    private ResultSet tableExistsResult;
    @Mock
    private ResultSet indexValidityResult;

    private PgVectorSchemaInitializer initializer;

    @BeforeEach
    void setUp() throws Exception {
        initializer = new PgVectorSchemaInitializer(dataSource);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(contains("to_regclass"))).thenReturn(tableExistsResult);
        when(tableExistsResult.next()).thenReturn(true);
        when(tableExistsResult.getBoolean(1)).thenReturn(true);

        when(connection.prepareStatement("SELECT pg_advisory_lock(?, ?)"))
                .thenReturn(advisoryLockStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)"))
                .thenReturn(advisoryUnlockStatement);
        when(connection.prepareStatement(contains("FROM pg_catalog.pg_index")))
                .thenReturn(indexValidityStatement);
        when(indexValidityStatement.executeQuery()).thenReturn(indexValidityResult);
    }

    @Test
    void invalidHnswIndexIsDroppedBeforeItIsRebuilt() throws Exception {
        when(indexValidityResult.next()).thenReturn(true);
        when(indexValidityResult.getBoolean(1)).thenReturn(false);

        initializer.run(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sqlCaptor.capture());
        List<String> executedSql = sqlCaptor.getAllValues();
        int dropIndex = indexOfSql(executedSql, "DROP INDEX CONCURRENTLY");
        int createIndex = indexOfSql(executedSql, "CREATE INDEX CONCURRENTLY");

        assertTrue(dropIndex >= 0, "invalid HNSW index must be dropped");
        assertTrue(createIndex > dropIndex, "HNSW index must be rebuilt after the drop");
        verify(indexValidityStatement).setString(
                1,
                "idx_system_knowledge_embedding_hnsw"
        );
    }

    @Test
    void validHnswIndexIsNotDropped() throws Exception {
        when(indexValidityResult.next()).thenReturn(true);
        when(indexValidityResult.getBoolean(1)).thenReturn(true);

        initializer.run(null);

        verify(statement, never()).execute(contains("DROP INDEX CONCURRENTLY"));
        verify(statement).execute(contains("CREATE INDEX CONCURRENTLY"));
    }

    private int indexOfSql(List<String> statements, String fragment) {
        for (int index = 0; index < statements.size(); index++) {
            if (statements.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }
}
