package fu.stockspace.stockspace_be.warehouse.entity;

/**
 * Trạng thái của Warehouse.
 *
 * AVAILABLE          — Kho sẵn sàng cho thuê
 * RENTED             — Đang có Tenant thuê
 * PENDING_APPROVAL   — Đã đăng bài, chờ Admin duyệt bài đăng
 * INACTIVE           — Owner tắt listing tạm thời
 */
public enum WarehouseStatus {
    AVAILABLE,
    RENTED,
    PENDING_APPROVAL,
    INACTIVE
}
