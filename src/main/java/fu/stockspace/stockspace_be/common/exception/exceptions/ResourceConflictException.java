package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;




@ResponseStatus(HttpStatus.CONFLICT)
@Getter
public class ResourceConflictException extends RuntimeException {

    private final ErrorCode errorCode;

    public ResourceConflictException(String message) {
        super(message);
        this.errorCode = null;
    }

    public ResourceConflictException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ResourceConflictException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
