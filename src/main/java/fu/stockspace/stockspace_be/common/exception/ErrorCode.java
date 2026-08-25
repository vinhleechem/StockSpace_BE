package fu.stockspace.stockspace_be.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;




@Getter
public enum ErrorCode {

    SYSTEM_ERROR("Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),


    UNAUTHENTICATED("Bạn chưa đăng nhập hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Tài khoản hoặc mật khẩu chưa chính xác", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("Token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("Refresh token không hợp lệ, đã hết hạn hoặc đã bị sử dụng lại", HttpStatus.UNAUTHORIZED),
    USER_LOCKED("Tài khoản đã bị khóa. Vui lòng liên hệ Admin", HttpStatus.LOCKED),


    INVALID_RESET_TOKEN("Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    RESET_TOKEN_EXPIRED("Đường dẫn đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại", HttpStatus.BAD_REQUEST),


    GOOGLE_AUTH_FAILED("Đăng nhập Google thất bại. Vui lòng thử lại", HttpStatus.BAD_REQUEST),
    CANNOT_LOGIN_GOOGLE_WITH_PASSWORD("Tài khoản này đã đăng ký qua Google. Vui lòng đăng nhập bằng Google", HttpStatus.CONFLICT),
    CANNOT_LOGIN_PASSWORD_WITH_GOOGLE("Tài khoản này đã đăng ký bằng email/mật khẩu. Vui lòng đăng nhập bằng email và mật khẩu", HttpStatus.CONFLICT),


    FORBIDDEN("Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),


    USER_NOT_FOUND("Người dùng không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS("Email đăng ký này đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    ROLE_NOT_SUPPORTED("Chỉ có thể tự đăng ký tài khoản với vai trò OWNER hoặc TENANT", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH("Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_ADMIN("Không thể xóa tài khoản Admin", HttpStatus.FORBIDDEN),
    CANNOT_DEACTIVATE_SELF("Không thể tự khóa tài khoản của chính mình", HttpStatus.FORBIDDEN),
    WEAK_PASSWORD("Mật khẩu phải có ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường và số", HttpStatus.BAD_REQUEST),


    ROLE_NOT_FOUND("Vai trò không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND("Quyền hạn không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    ROLE_ALREADY_EXISTS("Vai trò đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    PERMISSION_ALREADY_EXISTS("Quyền hạn đã tồn tại trong hệ thống", HttpStatus.CONFLICT),


    WAREHOUSE_NOT_FOUND("Kho bãi không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_OWNED("Bạn không phải chủ sở hữu của kho này", HttpStatus.FORBIDDEN),
    WAREHOUSE_NOT_AVAILABLE("Kho bãi hiện không khả dụng để thuê", HttpStatus.BAD_REQUEST),
    WAREHOUSE_ALREADY_VERIFIED("Kho bãi đã được xác minh trước đó", HttpStatus.CONFLICT),
    WAREHOUSE_TYPE_NOT_FOUND("Loại kho không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    WAREHOUSE_TYPE_ALREADY_EXISTS("Loại kho đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    WAREHOUSE_TYPE_IN_USE("Loại kho đang được sử dụng bởi một hoặc nhiều kho bãi, không thể xóa", HttpStatus.BAD_REQUEST),
    WAREHOUSE_HAS_ACTIVE_CONTRACTS("Không thể xoá kho đang có hợp đồng thuê hiệu lực", HttpStatus.BAD_REQUEST),
    WAREHOUSE_INVALID_STATUS_TRANSITION("Không thể chuyển sang trạng thái này", HttpStatus.BAD_REQUEST),
    WAREHOUSE_IMAGE_LIMIT_EXCEEDED("Số lượng ảnh vượt quá giới hạn tối đa (10 ảnh)", HttpStatus.BAD_REQUEST),


    BOOKING_NOT_FOUND("Yêu cầu thuê kho không tồn tại", HttpStatus.NOT_FOUND),
    BOOKING_ALREADY_PROCESSED("Yêu cầu thuê kho đã được xử lý (Approved/Rejected)", HttpStatus.BAD_REQUEST),
    BOOKING_DUPLICATE_PENDING("Bạn đã có một yêu cầu thuê kho đang chờ duyệt cho kho này", HttpStatus.CONFLICT),


    CONTRACT_NOT_FOUND("Hợp đồng thuê kho không tồn tại", HttpStatus.NOT_FOUND),


    INSPECTION_NOT_FOUND("Yêu cầu kiểm định không tồn tại", HttpStatus.NOT_FOUND),
    INSPECTION_ALREADY_SUBMITTED("Kiểm định đã được nộp hoặc đang xử lý", HttpStatus.BAD_REQUEST),
    INSPECTION_WAREHOUSE_NOT_AVAILABLE("Chỉ có thể gửi yêu cầu kiểm định khi kho đã được phê duyệt và đang hoạt động", HttpStatus.BAD_REQUEST),


    WALLET_NOT_FOUND("Ví điện tử không tồn tại", HttpStatus.NOT_FOUND),
    WALLET_INSUFFICIENT_BALANCE("Số dư ví không đủ để thực hiện giao dịch", HttpStatus.BAD_REQUEST),
    TRANSACTION_NOT_FOUND("Giao dịch không tồn tại", HttpStatus.NOT_FOUND),


    PACKAGE_NOT_FOUND("Gói dịch vụ không tồn tại", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE("Bạn đã có gói dịch vụ đang hoạt động", HttpStatus.CONFLICT),
    SUBSCRIPTION_NOT_FOUND("Subscription không tồn tại", HttpStatus.NOT_FOUND),


    WITHDRAW_REQUEST_NOT_FOUND("Yêu cầu rút tiền không tồn tại", HttpStatus.NOT_FOUND),
    WITHDRAW_ALREADY_PROCESSED("Yêu cầu rút tiền đã được xử lý", HttpStatus.BAD_REQUEST),




    NOTIFICATION_NOT_FOUND("Không tìm thấy thông báo", HttpStatus.NOT_FOUND),


    WAREHOUSE_BIN_NOT_FOUND("Ô chứa không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    LAYOUT_NOT_FOUND("Sơ đồ layout không tồn tại", HttpStatus.NOT_FOUND),
    LAYOUT_INVALID_COORDINATES("Tọa độ hoặc kích thước vượt giới hạn cho phép", HttpStatus.BAD_REQUEST),
    WAREHOUSE_BIN_NOT_EMPTY("Không thể xóa khu vực, kệ hoặc ô chứa này vì vẫn còn hàng tồn kho", HttpStatus.BAD_REQUEST),
    ZONE_NOT_FOUND("Khu vực không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    RACK_NOT_FOUND("Kệ hàng không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),


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


    STAFF_LIMIT_EXCEEDED("Số lượng nhân viên đã đạt giới hạn tối đa của gói dịch vụ hiện tại", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_NOT_FOUND("Lời mời nhân viên không tồn tại hoặc đã hết hạn", HttpStatus.NOT_FOUND),
    STAFF_INVITATION_EXPIRED("Lời mời nhân viên đã hết hạn. Vui lòng yêu cầu Tenant gửi lại lời mời mới", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_ALREADY_ACCEPTED("Lời mời này đã được sử dụng trước đó", HttpStatus.BAD_REQUEST),
    STAFF_INVITATION_DUPLICATE("Đã có lời mời đang chờ xác nhận gửi đến email này. Vui lòng kiểm tra hộp thư", HttpStatus.CONFLICT),
    STAFF_ALREADY_MEMBER("Email này đã là nhân viên kho đang hoạt động trong tổ chức của bạn", HttpStatus.CONFLICT),
    STAFF_CANNOT_INVITE_TENANT_OR_OWNER("Email này đã đăng ký tài khoản Tenant hoặc Chủ kho độc lập. Không thể mời làm Nhân viên kho", HttpStatus.BAD_REQUEST),
    STAFF_NOT_FOUND("Nhân viên không tồn tại hoặc đã bị xóa khỏi tổ chức", HttpStatus.NOT_FOUND),


    AUDIT_NOT_FOUND("Không tìm thấy phiếu kiểm kê", HttpStatus.NOT_FOUND),
    AUDIT_ALREADY_PROCESSED("Phiếu kiểm kê đã được xử lý (APPROVED/REJECTED)", HttpStatus.BAD_REQUEST),
    AUDIT_INVALID_STATUS("Trạng thái phiếu kiểm kê không hợp lệ để thực hiện hành động này", HttpStatus.BAD_REQUEST),


    CHAT_SESSION_NOT_FOUND("Phiên hội thoại không tồn tại", HttpStatus.NOT_FOUND),
    CHAT_SESSION_ACCESS_DENIED("Bạn không có quyền truy cập phiên hội thoại này", HttpStatus.FORBIDDEN),
    GEMINI_API_ERROR("Chatbot tạm thời không khả dụng, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    GEMINI_API_QUOTA_EXCEEDED("Chatbot đang bận, vui lòng thử lại sau ít phút", HttpStatus.TOO_MANY_REQUESTS),
    CHAT_TOOL_EXECUTION_ERROR("Không thể lấy dữ liệu yêu cầu, vui lòng thử lại", HttpStatus.BAD_GATEWAY),
    CHAT_PROVIDER_NOT_CONFIGURED("Chatbot chưa được cấu hình trên máy chủ", HttpStatus.SERVICE_UNAVAILABLE),
    CHAT_PROVIDER_UNAVAILABLE("Nhà cung cấp AI tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    CHAT_PROVIDER_INVALID_RESPONSE("Nhà cung cấp AI trả về dữ liệu không hợp lệ", HttpStatus.BAD_GATEWAY),
    CHAT_PROVIDER_TIMEOUT("Nhà cung cấp AI phản hồi quá thời gian cho phép", HttpStatus.GATEWAY_TIMEOUT),
    CHAT_PROVIDER_RATE_LIMITED("Chatbot đang bận, vui lòng thử lại sau ít phút", HttpStatus.TOO_MANY_REQUESTS),
    CHAT_PROVIDER_BUSY("Chatbot đang xử lý quá nhiều yêu cầu", HttpStatus.TOO_MANY_REQUESTS),
    CHAT_RATE_LIMIT_EXCEEDED("Bạn gửi yêu cầu quá nhanh, vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),


    REVIEW_NOT_FOUND("Đánh giá không tồn tại", HttpStatus.NOT_FOUND),
    REVIEW_ALREADY_EXISTS("Bạn đã đánh giá kho này cho hợp đồng này rồi", HttpStatus.CONFLICT),
    REVIEW_NOT_AUTHORIZED("Bạn không có quyền thực hiện thao tác này với đánh giá", HttpStatus.FORBIDDEN),
    REVIEW_EDIT_EXPIRED("Chỉ được sửa đánh giá trong vòng 7 ngày kể từ ngày tạo", HttpStatus.BAD_REQUEST),
    REVIEW_OWNER_ALREADY_REPLIED("Owner đã phản hồi đánh giá này rồi", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
}
