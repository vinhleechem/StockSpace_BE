package fu.stockspace.stockspace_be.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception đại diện cho HTTP 500 Internal Server Error.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class InternalServerException extends RuntimeException {

    public InternalServerException(String message) {
        super(message);
    }

    public InternalServerException(ErrorCode errorCode) {
        super(errorCode.getMessage());
    }

    public InternalServerException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
    }
}
