package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;




@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
@Getter
public class InternalServerException extends RuntimeException {

    private final ErrorCode errorCode;

    public InternalServerException(String message) {
        super(message);
        this.errorCode = null;
    }

    public InternalServerException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public InternalServerException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
