package fu.stockspace.stockspace_be.wms.dataexchange.xlsx;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public final class XlsxWorkbookWriter {

    private XlsxWorkbookWriter() {
    }

    public static Workbook newWorkbook() {
        return new XSSFWorkbook();
    }

    public static byte[] toBytes(Workbook workbook) {
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new XlsxFileException("Unable to render workbook", ex);
        }
    }

    public static Sheet addMetadataSheet(Workbook workbook, Map<String, ?> metadata) {
        Sheet sheet = workbook.createSheet("_META");
        int rowIndex = 0;
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(safeText(entry.getKey()));
            row.createCell(1).setCellValue(safeText(entry.getValue()));
        }
        // A hidden metadata sheet is not a security boundary. The password only
        // prevents accidental edits in Excel; authorization is always checked
        // against the authenticated user and the database.
        sheet.protectSheet("wms-data");
        workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
        return sheet;
    }

    public static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    public static CellStyle editableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    public static CellStyle readOnlyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setLocked(true);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    public static void writeHeaders(Row row, CellStyle style, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(safeText(headers[i]));
            cell.setCellStyle(style);
        }
    }

    /** Prefixes values that Excel could interpret as a formula. */
    public static String safeText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }
}
