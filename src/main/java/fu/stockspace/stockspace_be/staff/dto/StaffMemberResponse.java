package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về thông tin 1 nhân viên trong danh sách quản lý của Tenant.
 * GET /api/tenant/staffs
 */
@Getter
@Builder
public class StaffMemberResponse {

    /** ID của bản ghi membership (dùng khi Tenant muốn xóa/khóa nhân viên) */
    private UUID memberId;

    /** ID của User (identity thực sự) */
    private UUID userId;

    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;

    /** Trạng thái hoạt động — false khi bị khóa do gói hết hạn/downgrade */
    private boolean isActive;

    /** Thời điểm nhân viên gia nhập (click link xác nhận) */
    private LocalDateTime joinedAt;
}
