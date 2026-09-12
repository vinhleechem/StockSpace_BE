package fu.stockspace.stockspace_be.wms.dataexchange.xlsx;

public class XlsxFileException extends RuntimeException {

    public XlsxFileException(String message) {
        super(message);
    }

    public XlsxFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
