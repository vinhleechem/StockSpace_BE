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

    // OTP / Password Reset Errors
    INVALID_RESET_TOKEN("Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    RESET_TOKEN_EXPIRED("Đường dẫn đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại", HttpStatus.BAD_REQUEST),

    // Google OAuth Errors
    GOOGLE_AUTH_FAILED("Đăng nhập Google thất bại. Vui lòng thử lại", HttpStatus.BAD_REQUEST),
    CANNOT_LOGIN_GOOGLE_WITH_PASSWORD("Tài khoản này đã đăng ký qua Google. Vui lòng đăng nhập bằng Google", HttpStatus.CONFLICT),
    CANNOT_LOGIN_PASSWORD_WITH_GOOGLE("Tài khoản này đã đăng ký bằng email/mật khẩu. Vui lòng đăng nhập bằng email và mật khẩu", HttpStatus.CONFLICT),

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
    TRANSACTION_NOT_FOUND("Giao dịch không tồn tại", HttpStatus.NOT_FOUND),

    // Service Package & Subscription Errors
    PACKAGE_NOT_FOUND("Gói dịch vụ không tồn tại", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE("Bạn đã có gói dịch vụ đang hoạt động", HttpStatus.CONFLICT),
    SUBSCRIPTION_NOT_FOUND("Subscription không tồn tại", HttpStatus.NOT_FOUND),

    // Withdraw Errors
    WITHDRAW_REQUEST_NOT_FOUND("Yêu cầu rút tiền không tồn tại", HttpStatus.NOT_FOUND),
    WITHDRAW_ALREADY_PROCESSED("Yêu cầu rút tiền đã được xử lý", HttpStatus.BAD_REQUEST),

    // Dispute Errors
    DISPUTE_NOT_FOUND("Tranh chấp không tồn tại", HttpStatus.NOT_FOUND),
    DISPUTE_ALREADY_OPEN("Đã có tranh chấp đang mở cho hợp đồng này", HttpStatus.CONFLICT),

    // Notification Errors
    NOTIFICATION_NOT_FOUND("Không tìm thấy thông báo", HttpStatus.NOT_FOUND),

    // Bin Errors
    WAREHOUSE_BIN_NOT_FOUND("Ô chứa không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    LAYOUT_NOT_FOUND("Sơ đồ layout không tồn tại", HttpStatus.NOT_FOUND),
    LAYOUT_INVALID_COORDINATES("Tọa độ hoặc kích thước vượt giới hạn cho phép", HttpStatus.BAD_REQUEST),
    WAREHOUSE_BIN_NOT_EMPTY("Không thể xóa khu vực, kệ hoặc ô chứa này vì vẫn còn hàng tồn kho", HttpStatus.BAD_REQUEST),
    ZONE_NOT_FOUND("Khu vực không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    RACK_NOT_FOUND("Kệ hàng không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),

    // Stock & WMS Errors (shared with future tasks)
    PRODUCT_CATEGORY_NOT_FOUND("Không tìm thấy danh mục sản phẩm", HttpStatus.NOT_FOUND),
    SKU_NOT_FOUND("Không tìm thấy SKU", HttpStatus.NOT_FOUND),
    SKU_CODE_DUPLICATE("Mã SKU đã tồn tại", HttpStatus.CONFLICT),
    STOCK_BATCH_NOT_FOUND("Không tìm thấy lô hàng tồn kho", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT_QUANTITY("Số dư tồn kho không đủ", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_REQUIRED("Yêu cầu gói dịch vụ (Subscription) còn hiệu lực để thực hiện hành động này", HttpStatus.FORBIDDEN),
    PRODUCT_CATEGORY_IN_USE("Không thể xóa danh mục sản phẩm vì đang có SKU liên kết", HttpStatus.BAD_REQUEST),
    SKU_IN_USE("Không thể xóa SKU vì đang có lô hàng tồn kho liên kết", HttpStatus.BAD_REQUEST),
    RECEIPT_NOT_FOUND("Không tìm thấy phiếu xuất nhập kho", HttpStatus.NOT_FOUND),
    RECEIPT_ALREADY_PROCESSED("Phiếu xuất nhập kho đã được xử lý", HttpStatus.BAD_REQUEST),
    CONFIG_NOT_FOUND("Cấu hình hệ thống không tồn tại", HttpStatus.NOT_FOUND),
    CONFIG_INVALID_VALUE("Giá trị cấu hình không hợp lệ", HttpStatus.BAD_REQUEST),
    UOM_NOT_FOUND("Không tìm thấy đơn vị tính", HttpStatus.NOT_FOUND),

    // Staff Membership Errors
    STAFF_LIMIT_EXCEEDED("Số lượng nhân viên đã đạt giới hạn tối đa của gói dịch vụ hiện tại", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_NOT_FOUND("Lời mời nhân viên không tồn tại hoặc đã hết hạn", HttpStatus.NOT_FOUND),
    STAFF_INVITATION_EXPIRED("Lời mời nhân viên đã hết hạn. Vui lòng yêu cầu Tenant gửi lại lời mời mới", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_ALREADY_ACCEPTED("Lời mời này đã được sử dụng trước đó", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_DUPLICATE("Đã có lời mời đang chờ xác nhận gửi đến email này. Vui lòng kiểm tra hộp thư", HttpStatus.CONFLICT),
    STAFF_ALREADY_MEMBER("Email này đã là nhân viên kho đang hoạt động trong tổ chức của bạn", HttpStatus.CONFLICT),
    STAFF_NOT_FOUND("Nhân viên không tồn tại hoặc đã bị xóa khỏi tổ chức", HttpStatus.NOT_FOUND),

    // Inventory Audit Errors (Dev B)
    AUDIT_NOT_FOUND("Không tìm thấy phiếu kiểm kê", HttpStatus.NOT_FOUND),
    AUDIT_ALREADY_PROCESSED("Phiếu kiểm kê đã được xử lý (APPROVED/REJECTED)", HttpStatus.BAD_REQUEST),
    AUDIT_INVALID_STATUS("Trạng thái phiếu kiểm kê không hợp lệ để thực hiện hành động này", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
