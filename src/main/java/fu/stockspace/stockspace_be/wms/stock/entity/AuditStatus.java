package fu.stockspace.stockspace_be.wms.stock.entity;




public enum AuditStatus {
    /** Historical state retained only so old rows can still be read safely. */
    PENDING,
    /** Plan has been created but counting has not started. */
    DRAFT,
    /** Counting is in progress and its scope is movement-locked. */
    IN_PROGRESS,
    SUBMITTED,
    /** Reviewer asked the counter to perform another count round. */
    RECOUNT_REQUIRED,
    APPROVED,
    /** Historical terminal state; the canonical flow uses CANCELLED/RECOUNT_REQUIRED. */
    REJECTED,
    CANCELLED
}
