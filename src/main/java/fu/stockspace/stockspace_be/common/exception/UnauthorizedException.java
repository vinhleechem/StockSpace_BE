package fu.stockspace.stockspace_be.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception đại diện cho HTTP 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
    }

    public UnauthorizedException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
    }
}
