package fu.stockspace.stockspace_be.wms.dataexchange.xlsx;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class XlsxFileException extends RuntimeException {

    private final ErrorCode errorCode;

    public XlsxFileException(String message) {
        super(message);
        this.errorCode = ErrorCode.WMS_IMPORT_FILE_INVALID;
    }

    public XlsxFileException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.WMS_IMPORT_FILE_INVALID;
    }

    public XlsxFileException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public XlsxFileException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
