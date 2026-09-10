package fu.stockspace.stockspace_be.wallet.entity;



public enum TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    /** No payment result was received before the VNPAY payment window closed. */
    EXPIRED
}
