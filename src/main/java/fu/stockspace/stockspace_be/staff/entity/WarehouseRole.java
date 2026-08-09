package fu.stockspace.stockspace_be.staff.entity;

/**
 * Enum đại diện cho các vai trò chuẩn hóa của Staff trong từng Kho cụ thể.
 */
public enum WarehouseRole {
    /** Quản lý kho: có đầy đủ quyền thao tác WMS và duyệt phiếu / kiểm kê tại kho */
    MANAGER,

    /** Nhân viên vận hành: quyền tạo/xử lý phiếu nhập/xuất kho */
    OPERATOR,

    /** Nhân viên kiểm kê: chuyên trách thực hiện đếm & lập biên bản kiểm kê tồn kho */
    INSPECTOR
}
