package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response trả về khi FE validate token trước khi hiện form nhập mật khẩu.
 * GET /api/auth/staff/invite?token=xxx
 *
 * FE dùng response này để pre-fill email và hiển thị tên Tenant mời.
 */
@Getter
@Builder
public class InvitationPreviewResponse {

    private String email;
    private String fullName;
    private String tenantName;
    private String tenantEmail;

    /** true nếu token hợp lệ và chưa hết hạn */
    private boolean valid;

    /** Thông báo lỗi nếu valid = false */
    private String message;
}
