package fu.stockspace.stockspace_be.wms.transfer.entity;

/**
 * Condition recorded when returned stock is received at the source warehouse.
 * Both conditions are booked into source inventory; REJECTED keeps its audit note.
 */
public enum StockTransferReturnDisposition {
    GOOD,
    REJECTED
}
