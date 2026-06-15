package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO nhận tham số tìm kiếm kho — dùng cho cả Public và Admin search.
 */
@Getter
@Setter
public class WarehouseSearchRequest {

    /** Từ khoá tìm theo tên hoặc địa chỉ */
    private String keyword;

    /** Lọc theo trạng thái (null = tất cả) */
    private WarehouseStatus status;

    /** Lọc giá thuê tối thiểu (VNĐ/tháng) */
    private BigDecimal minPrice;

    /** Lọc giá thuê tối đa */
    private BigDecimal maxPrice;

    /** Lọc sức chứa tối thiểu (m²) */
    private BigDecimal minCapacity;

    // ==================== Admin only ====================

    /** Admin: lọc theo trạng thái xác minh */
    private Boolean isVerified;
}
