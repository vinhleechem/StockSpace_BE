package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;




@ResponseStatus(HttpStatus.UNAUTHORIZED)
@Getter
public class UnauthorizedException extends RuntimeException {

    private final ErrorCode errorCode;

    public UnauthorizedException(String message) {
        super(message);
        this.errorCode = null;
    }

    public UnauthorizedException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public UnauthorizedException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
