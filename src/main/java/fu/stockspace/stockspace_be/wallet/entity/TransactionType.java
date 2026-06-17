package fu.stockspace.stockspace_be.wallet.entity;
/**
 * Enum các loại giao dịch ví.
 */
public enum TransactionType {
    TOP_UP,             // Nạp tiền
    WITHDRAWAL,         // Rút tiền
    DEPOSIT_PAYMENT,    // Thanh toán tiền cọc thuê kho
    DEPOSIT_REFUND,     // Hoàn tiền cọc
    PACKAGE_PAYMENT,    // Thanh toán mua gói dịch vụ
    COMMISSION          // Phí hoa hồng hệ thống
}