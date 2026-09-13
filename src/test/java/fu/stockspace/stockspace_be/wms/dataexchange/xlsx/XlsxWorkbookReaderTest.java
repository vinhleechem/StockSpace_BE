package fu.stockspace.stockspace_be.wms.dataexchange.xlsx;

import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XlsxWorkbookReaderTest {

    private final XlsxWorkbookReader reader = new XlsxWorkbookReader(new DataExchangeProperties());

    @Test
    void opensValidXlsxAndPreservesMetadata() {
        Workbook workbook = XlsxWorkbookWriter.newWorkbook();
        Sheet sheet = workbook.createSheet("DATA");
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("SKU-01");
        XlsxWorkbookWriter.addMetadataSheet(workbook, Map.of("schema_version", "1.0"));

        byte[] bytes = XlsxWorkbookWriter.toBytes(workbook);
        try (Workbook reopened = reader.open(bytes, "catalog.xlsx", Set.of("DATA"))) {
            assertEquals("SKU-01", reopened.getSheet("DATA").getRow(0).getCell(0).getStringCellValue());
            assertEquals("_META", reopened.getSheetAt(1).getSheetName());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void rejectsWrongExtensionAndEmptyFile() {
        assertThrows(XlsxFileException.class, () -> reader.open(new byte[]{'P', 'K'}, "data.xls"));
        assertThrows(XlsxFileException.class, () -> reader.open(new byte[0], "data.xlsx"));
    }

    @Test
    void rejectsFormulaInEditableSheet() {
        Workbook workbook = XlsxWorkbookWriter.newWorkbook();
        Sheet sheet = workbook.createSheet("DATA");
        sheet.createRow(0).createCell(0).setCellFormula("1+1");
        byte[] bytes = XlsxWorkbookWriter.toBytes(workbook);

        assertThrows(XlsxFileException.class, () -> reader.open(bytes, "data.xlsx", Set.of("DATA")));
    }

    @Test
    void neutralizesSpreadsheetFormulaPrefixes() {
        assertEquals("'=SUM(A1)", XlsxWorkbookWriter.safeText("=SUM(A1)"));
        assertEquals("plain", XlsxWorkbookWriter.safeText("plain"));
    }
}
