package fu.stockspace.stockspace_be.common.exception;

import lombok.Getter;

/**
 * Custom Exception dùng chung cho toàn bộ ứng dụng StockSpace.
 * Kế thừa RuntimeException để tự động rollback transaction khi xảy ra lỗi.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
