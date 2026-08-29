package fu.stockspace.stockspace_be;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;






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
