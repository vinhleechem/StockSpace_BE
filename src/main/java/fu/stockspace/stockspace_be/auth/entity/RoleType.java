package fu.stockspace.stockspace_be.auth.entity;

/**
 * Enum định nghĩa tên các role MẶC ĐỊNH trong hệ thống.
 *
 * Dùng để:
 * 1. Seed dữ liệu vào bảng roles lúc khởi động app (DataInitializer)
 * 2. Validate role khi tự register (chỉ OWNER và TENANT)
 *
 * ⚠️ Đây KHÔNG phải là Role entity. Role entity nằm ở Role.java.
 * Admin có thể tạo thêm role mới qua API — chúng sẽ không có trong enum này.
 */
public enum RoleType {
    ROLE_ADMIN,
    ROLE_OWNER,
    ROLE_TENANT,
    ROLE_STAFF,
    ROLE_INSPECTOR
}
