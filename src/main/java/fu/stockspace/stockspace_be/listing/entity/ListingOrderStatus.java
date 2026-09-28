package fu.stockspace.stockspace_be.listing.entity;

public enum ListingOrderStatus {
    PAID,
    @Deprecated
    PENDING_APPROVAL,
    @Deprecated
    ACTIVATED,
    REFUNDED,
    TERMINATED
}
