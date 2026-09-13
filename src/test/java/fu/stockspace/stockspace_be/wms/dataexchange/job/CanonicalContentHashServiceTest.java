package fu.stockspace.stockspace_be.wms.dataexchange.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CanonicalContentHashServiceTest {

    private final CanonicalContentHashService service = new CanonicalContentHashService(new ObjectMapper());

    @Test
    void canonicalHashIgnoresMapOrderAndDecimalScaleButIncludesScope() {
        WmsImportRowInput first = new WmsImportRowInput("SKUS", 2, null,
                Map.of("name", "Cà phê", "quantity", new BigDecimal("10.0")), List.of());
        WmsImportRowInput second = new WmsImportRowInput("SKUS", 1, null,
                Map.of("name", "Tea", "quantity", 2), List.of());
        String left = service.hash(WmsImportType.SKU_CATALOG,
                Map.of("tenant_id", UUID.fromString("00000000-0000-0000-0000-000000000001")),
                List.of(first, second));
        String right = service.hash(WmsImportType.SKU_CATALOG,
                Map.of("tenant_id", UUID.fromString("00000000-0000-0000-0000-000000000001")),
                List.of(second, new WmsImportRowInput("SKUS", 2, null,
                        Map.of("quantity", new BigDecimal("10.00"), "name", "Cà phê"), List.of())));
        String differentScope = service.hash(WmsImportType.SKU_CATALOG,
                Map.of("tenant_id", UUID.fromString("00000000-0000-0000-0000-000000000002")),
                List.of(first, second));

        assertEquals(left, right);
        assertNotEquals(left, differentScope);
    }
}
