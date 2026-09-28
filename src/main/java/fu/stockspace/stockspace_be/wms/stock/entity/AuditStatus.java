package fu.stockspace.stockspace_be.wms.stock.entity;




public enum AuditStatus {
    PENDING,
    DRAFT,
    IN_PROGRESS,
    SUBMITTED,
    EDIT_REQUESTED,
    REOPENED,
    RECOUNT_REQUIRED,
    APPROVED,
    REJECTED,
    CANCELLED
}
