package fu.stockspace.stockspace_be.wallet.entity;
/**
 * Enum các trạng thái giao dịch.
 */
public enum TransactionStatus {
    PENDING,    // Chờ xử lý / Chờ chuyển khoản
    SUCCESS,    // Thành công
    FAILED      // Thất bại
}