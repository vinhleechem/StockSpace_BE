package fu.stockspace.stockspace_be.staff.entity;

/**
 * Trạng thái phân công kho của Staff.
 */
public enum AssignmentStatus {
    /** Đang được phân công làm việc tại kho */
    ACTIVE,

    /** Đã bị thu hồi phân công kho / Nhân viên nghỉ việc */
    REVOKED,

    /** Đã hết hạn thời gian phân công */
    EXPIRED
}
