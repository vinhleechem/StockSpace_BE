package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogWorkbookFormatTest {

    @Test
    void editableCellsAreUnlockedWhileReadOnlyCellsRemainLocked() throws Exception {
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            assertFalse(XlsxWorkbookWriter.editableStyle(workbook).getLocked());
            assertTrue(XlsxWorkbookWriter.readOnlyStyle(workbook).getLocked());
        }
    }
}
