package fu.stockspace.stockspace_be.booking.entity;

/**
 * Trạng thái phê duyệt dùng chung cho Booking Request, Withdraw Request, Adjustment Note, v.v.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
