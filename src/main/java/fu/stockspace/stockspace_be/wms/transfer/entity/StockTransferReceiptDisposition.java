package fu.stockspace.stockspace_be.wms.transfer.entity;

public enum StockTransferReceiptDisposition {
    GOOD,
    @Deprecated
    QUARANTINE,
    DAMAGED,
    REJECTED
}
