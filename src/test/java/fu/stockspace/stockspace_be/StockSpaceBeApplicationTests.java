package fu.stockspace.stockspace_be;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This test starts the complete application and therefore requires a dedicated
 * PostgreSQL database. It is opt-in so a normal unit-test run can never mutate
 * a developer or production-like database through DataInitializer/Hibernate.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "RUN_DATABASE_INTEGRATION_TESTS",
        matches = "true"
)
class StockSpaceBeApplicationTests {

    @Test
    void contextLoads() {
    }

}
