package fu.stockspace.stockspace_be.wms.transfer.entity;

public enum StockTransferAttemptStatus {
    PLANNED,
    IN_TRANSIT,
    ARRIVED,
    RECEIVING,
    RECEIVED,
    REJECTED,
    PARTIALLY_RECEIVED,
    RETURNED,
    CANCELLED
}
