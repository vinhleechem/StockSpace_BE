package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;




@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode.getMessage());
    }

    public ResourceNotFoundException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
    }
}
