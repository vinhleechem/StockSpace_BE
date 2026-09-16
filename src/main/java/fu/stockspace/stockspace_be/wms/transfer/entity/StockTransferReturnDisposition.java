package fu.stockspace.stockspace_be.wms.transfer.entity;

/**
 * Condition recorded when returned stock is received at the source warehouse.
 * A return is either usable stock or a rejected/non-usable record.
 */
public enum StockTransferReturnDisposition {
    GOOD,
    REJECTED
}
