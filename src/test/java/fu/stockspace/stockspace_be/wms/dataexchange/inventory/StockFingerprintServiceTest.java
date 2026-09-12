package fu.stockspace.stockspace_be.wms.dataexchange.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StockFingerprintServiceTest {

    @Test
    void canonicalHashChangesWhenWarehouseScopeChanges() {
        CanonicalContentHashService service = new CanonicalContentHashService(new ObjectMapper());
        String first = service.sha256("warehouse=one|batch=a|quantity=1");
        String second = service.sha256("warehouse=two|batch=a|quantity=1");
        assertNotEquals(first, second);
    }
}
