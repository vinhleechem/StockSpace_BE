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
    PASSWORD_MISMATCH("Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_ADMIN("Không thể xóa tài khoản Admin", HttpStatus.FORBIDDEN),
    CANNOT_DEACTIVATE_SELF("Không thể tự khóa tài khoản của chính mình", HttpStatus.FORBIDDEN),
    WEAK_PASSWORD("Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường và số", HttpStatus.BAD_REQUEST),

    // Role & Permission Errors
    ROLE_NOT_FOUND("Vai trò không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND("Quyền hạn không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("Vai trò đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    PERMISSION_ALREADY_EXISTS("Quyền hạn đã tồn tại trong hệ thống", HttpStatus.CONFLICT),

    // Warehouse Errors
    WAREHOUSE_NOT_FOUND("Kho bãi không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_OWNED("Bạn không phải chủ sở hữu của kho này", HttpStatus.FORBIDDEN),
    WAREHOUSE_NOT_AVAILABLE("Kho bãi hiện không khả dụng để thuê", HttpStatus.BAD_REQUEST),
    WAREHOUSE_ALREADY_VERIFIED("Kho bãi đã được xác minh trước đó", HttpStatus.CONFLICT),
    WAREHOUSE_TYPE_NOT_FOUND("Loại kho không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    WAREHOUSE_TYPE_ALREADY_EXISTS("Loại kho đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    WAREHOUSE_TYPE_IN_USE("Loại kho đang được sử dụng bởi một hoặc nhiều kho bãi, không thể xóa", HttpStatus.BAD_REQUEST),
    WAREHOUSE_CANNOT_DELETE_RENTED("Không thể xoá kho đang có Tenant thuê", HttpStatus.BAD_REQUEST),
    WAREHOUSE_INVALID_STATUS_TRANSITION("Không thể chuyển sang trạng thái này", HttpStatus.BAD_REQUEST),
    WAREHOUSE_IMAGE_LIMIT_EXCEEDED("Số lượng ảnh vượt quá giới hạn tối đa (10 ảnh)", HttpStatus.BAD_REQUEST),

    // Booking Errors
    BOOKING_NOT_FOUND("Yêu cầu thuê kho không tồn tại", HttpStatus.NOT_FOUND),
    BOOKING_ALREADY_PROCESSED("Yêu cầu thuê kho đã được xử lý (Approved/Rejected)", HttpStatus.BAD_REQUEST),
    BOOKING_DUPLICATE_PENDING("Bạn đã có một yêu cầu thuê kho đang chờ duyệt cho kho này", HttpStatus.CONFLICT),

    // Contract Errors
    CONTRACT_NOT_FOUND("Hợp đồng thuê kho không tồn tại", HttpStatus.NOT_FOUND),
    CONTRACT_ALREADY_CONFIRMED("Bạn đã xác nhận bàn giao hoặc hợp đồng đã hoàn thành", HttpStatus.BAD_REQUEST),

    // Inspection Errors
    INSPECTION_NOT_FOUND("Yêu cầu kiểm định không tồn tại", HttpStatus.NOT_FOUND),
    INSPECTION_ALREADY_SUBMITTED("Kiểm định đã được nộp hoặc đang xử lý", HttpStatus.BAD_REQUEST),

    // Wallet & Transaction Errors
    WALLET_NOT_FOUND("Ví điện tử không tồn tại", HttpStatus.NOT_FOUND),
    WALLET_INSUFFICIENT_BALANCE("Số dư ví không đủ để thực hiện giao dịch", HttpStatus.BAD_REQUEST),

    // Service Package & Subscription Errors
    PACKAGE_NOT_FOUND("Gói dịch vụ không tồn tại", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE("Bạn đã có gói dịch vụ đang hoạt động", HttpStatus.CONFLICT),
    SUBSCRIPTION_NOT_FOUND("Subscription không tồn tại", HttpStatus.NOT_FOUND),

    // Withdraw Errors
    WITHDRAW_REQUEST_NOT_FOUND("Yêu cầu rút tiền không tồn tại", HttpStatus.NOT_FOUND),
    WITHDRAW_ALREADY_PROCESSED("Yêu cầu rút tiền đã được xử lý", HttpStatus.BAD_REQUEST),

    // Dispute Errors
    DISPUTE_NOT_FOUND("Tranh chấp không tồn tại", HttpStatus.NOT_FOUND),
    DISPUTE_ALREADY_OPEN("Đã có tranh chấp đang mở cho hợp đồng này", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
