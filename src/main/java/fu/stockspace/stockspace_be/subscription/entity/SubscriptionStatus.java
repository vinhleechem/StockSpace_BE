package fu.stockspace.stockspace_be.subscription.entity;
/**
 * Enum các trạng thái của gói đăng ký.
 */
public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    /** Đã bị thay thế / hủy sớm do nâng cấp gói mới */
    SUPERSEDED
}