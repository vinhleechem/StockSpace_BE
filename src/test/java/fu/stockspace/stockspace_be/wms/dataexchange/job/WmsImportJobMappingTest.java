package fu.stockspace.stockspace_be.wms.dataexchange.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WmsImportJobMappingTest {

    @Test
    void definesOnlyThePlannedImportScopesAndStatuses() {
        assertEquals(3, WmsImportType.values().length);
        assertEquals(4, WmsImportJobStatus.values().length);
        assertEquals(WmsImportType.SKU_CATALOG, WmsImportType.valueOf("SKU_CATALOG"));
        assertEquals(WmsImportJobStatus.APPLIED, WmsImportJobStatus.valueOf("APPLIED"));
    }
}
