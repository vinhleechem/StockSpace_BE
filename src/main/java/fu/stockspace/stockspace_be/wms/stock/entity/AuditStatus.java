package fu.stockspace.stockspace_be.wms.stock.entity;

/**
 * Trạng thái vòng đời của phiếu kiểm kê kho.
 */
public enum AuditStatus {
    PENDING,    // Vừa tạo, chưa điền kết quả
    SUBMITTED,  // Đã nộp kết quả kiểm đếm, chờ duyệt
    APPROVED,   // Đã duyệt, tồn kho đã được điều chỉnh
    REJECTED    // Bị từ chối
}
