package fu.stockspace.stockspace_be.wms.dataexchange.xlsx;

import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class XlsxWorkbookReader {

    private static final String XLSX_EXTENSION = ".xlsx";
    private static final double MIN_INFLATE_RATIO = 0.01d;

    private final DataExchangeProperties properties;

    public XlsxWorkbookReader(DataExchangeProperties properties) {
        this.properties = properties;
    }

    /**
     * Opens a user workbook after applying the file-level safety checks.
     * The caller owns and must close the returned workbook.
     */
    public Workbook open(byte[] content, String originalFilename) {
        validateFile(content, originalFilename);
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);

        try (InputStream input = new ByteArrayInputStream(content)) {
            if (FileMagic.valueOf(input) != FileMagic.OOXML) {
                throw new XlsxFileException("Only OOXML .xlsx workbooks are supported");
            }
        } catch (IOException ex) {
            throw new XlsxFileException("The uploaded workbook cannot be read", ex);
        }

        try (InputStream input = new ByteArrayInputStream(content)) {
            return WorkbookFactory.create(input);
        } catch (IOException | RuntimeException ex) {
            throw new XlsxFileException("The uploaded workbook is invalid or encrypted", ex);
        }
    }

    public Workbook open(byte[] content, String originalFilename, Set<String> editableSheets) {
        Workbook workbook = open(content, originalFilename);
        try {
            rejectFormulaCells(workbook, editableSheets);
            return workbook;
        } catch (RuntimeException ex) {
            try {
                workbook.close();
            } catch (IOException closeException) {
                ex.addSuppressed(closeException);
            }
            throw ex;
        }
    }

    public void rejectFormulaCells(Workbook workbook, Set<String> editableSheets) {
        for (Sheet sheet : workbook) {
            if (!editableSheets.contains(sheet.getSheetName())) {
                continue;
            }
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw new XlsxFileException("Formula cells are not allowed in editable sheets: "
                                + sheet.getSheetName() + "!" + cell.getAddress());
                    }
                }
            }
        }
    }

    private void validateFile(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new XlsxFileException("The uploaded workbook is empty");
        }
        if (content.length > properties.getMaxFileBytes()) {
            throw new XlsxFileException("The uploaded workbook exceeds the configured size limit");
        }
        if (originalFilename == null
                || !originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(XLSX_EXTENSION)
                || originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsm")) {
            throw new XlsxFileException("Only .xlsx files are supported");
        }
    }
}
