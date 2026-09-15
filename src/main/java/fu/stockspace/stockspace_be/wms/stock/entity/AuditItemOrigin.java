package fu.stockspace.stockspace_be.wms.stock.entity;

/**
 * Describes how an audit item entered the current count round.
 */
public enum AuditItemOrigin {
    SNAPSHOT,
    UNEXPECTED
}
