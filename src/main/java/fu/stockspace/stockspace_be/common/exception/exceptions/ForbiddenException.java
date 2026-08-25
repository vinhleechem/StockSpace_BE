package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;




@ResponseStatus(HttpStatus.FORBIDDEN)
@Getter
public class ForbiddenException extends RuntimeException {

    private final ErrorCode errorCode;

    public ForbiddenException(String message) {
        super(message);
        this.errorCode = null;
    }

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
