package fu.stockspace.stockspace_be.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Định nghĩa mã lỗi nghiệp vụ (Business Error Codes) và HTTP Status tương ứng.
 */
@Getter
public enum ErrorCode {
    // System Errors
    SYSTEM_ERROR("Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),

    // Authentication Errors
    UNAUTHENTICATED("Bạn chưa đăng nhập hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Tài khoản hoặc mật khẩu chưa chính xác", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("Token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("Refresh token không hợp lệ, đã hết hạn hoặc đã bị sử dụng lại", HttpStatus.UNAUTHORIZED),
    USER_LOCKED("Tài khoản đã bị khóa. Vui lòng liên hệ Admin", HttpStatus.LOCKED),

    // Authorization Errors
    FORBIDDEN("Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),

    // User Errors
    USER_NOT_FOUND("Người dùng không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("Email đăng ký này đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    ROLE_NOT_SUPPORTED("Chỉ có thể tự đăng ký tài khoản với vai trò OWNER hoặc TENANT", HttpStatus.BAD_REQUEST),

    // Role & Permission Errors
    ROLE_NOT_FOUND("Vai trò không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND("Quyền hạn không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("Vai trò đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    PERMISSION_ALREADY_EXISTS("Quyền hạn đã tồn tại trong hệ thống", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
