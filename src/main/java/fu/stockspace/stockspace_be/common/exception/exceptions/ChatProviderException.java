package fu.stockspace.stockspace_be.common.exception.exceptions;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.Getter;

/**
 * Typed failure raised by the external chat provider integration.
 */
@Getter
public class ChatProviderException extends RuntimeException {

    private final ErrorCode errorCode;

    public ChatProviderException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
