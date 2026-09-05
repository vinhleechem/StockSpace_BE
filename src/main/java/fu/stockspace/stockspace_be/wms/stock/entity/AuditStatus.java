package fu.stockspace.stockspace_be.wms.stock.entity;




public enum AuditStatus {
    /** Legacy state: created and already snapshotted by the v1 endpoint. */
    PENDING,
    /** v2 plan has been created but counting has not started. */
    DRAFT,
    /** v2 counting is in progress and its scope is movement-locked. */
    IN_PROGRESS,
    SUBMITTED,
    /** Reviewer asked the counter to perform another count round. */
    RECOUNT_REQUIRED,
    APPROVED,
    REJECTED,
    CANCELLED
}
