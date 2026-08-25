package fu.stockspace.stockspace_be.common.entity;

/** Shared approval state for receipts and withdrawal requests. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
