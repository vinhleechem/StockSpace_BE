package fu.stockspace.stockspace_be.staff.entity;

/**
 * Trạng thái lời mời nhân viên kho.
 */
public enum InvitationStatus {
    /** Đã gửi, chờ nhân viên xác nhận */
    PENDING,
    /** Nhân viên đã click link và thiết lập mật khẩu thành công */
    ACCEPTED,
    /** Đã quá 48 giờ mà chưa được xác nhận */
    EXPIRED
}
