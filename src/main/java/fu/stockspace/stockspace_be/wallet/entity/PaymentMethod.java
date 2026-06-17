package fu.stockspace.stockspace_be.wallet.entity;
/**
 * Enum các phương thức thanh toán.
 */
public enum PaymentMethod {
    BANK_TRANSFER,      // Chuyển khoản ngân hàng (qua SePay)
    VNPAY,              // Cổng VNPAY
    MOMO,               // Ví Momo
    WALLET              // Thanh toán bằng số dư ví
}