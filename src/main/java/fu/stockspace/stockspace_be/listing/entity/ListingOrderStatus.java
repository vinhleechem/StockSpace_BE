package fu.stockspace.stockspace_be.listing.entity;

public enum ListingOrderStatus {
    /** New paid publication order. */
    PAID,
    /** @deprecated Kept temporarily for migration compatibility. */
    @Deprecated
    PENDING_APPROVAL,
    /** @deprecated Kept temporarily for migration compatibility. */
    @Deprecated
    ACTIVATED,
    /** Publication was cancelled before its scheduled start. */
    REFUNDED,
    /** Active publication was stopped without refund. */
    TERMINATED
}
