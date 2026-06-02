package fu.stockspace.stockspace_be.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception đại diện cho HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(ErrorCode errorCode) {
        super(errorCode.getMessage());
    }

    public BadRequestException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
    }
}
