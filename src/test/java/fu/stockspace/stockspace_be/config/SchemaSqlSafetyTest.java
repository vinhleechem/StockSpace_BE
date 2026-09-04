package fu.stockspace.stockspace_be.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaSqlSafetyTest {

    @Test
    void schemaSqlMustNotContainPostgresDollarQuotedBlocks() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            assertNotNull(input, "schema.sql must be available on the runtime classpath");
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(
                    schema.matches("(?s).*\\bDO\\s+\\$[^$]*\\$.*"),
                    "schema.sql is parsed by Spring using semicolon separators; "
                            + "PostgreSQL dollar-quoted blocks must live in ops/migrations instead"
            );
        }
    }
}
