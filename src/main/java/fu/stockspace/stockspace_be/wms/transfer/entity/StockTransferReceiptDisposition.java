package fu.stockspace.stockspace_be.wms.transfer.entity;

public enum StockTransferReceiptDisposition {
    GOOD,
    /**
     * Legacy value kept so previously persisted receipts can still be read.
     * New receipts must use GOOD, DAMAGED, or REJECTED instead.
     */
    @Deprecated
    QUARANTINE,
    DAMAGED,
    REJECTED
}
