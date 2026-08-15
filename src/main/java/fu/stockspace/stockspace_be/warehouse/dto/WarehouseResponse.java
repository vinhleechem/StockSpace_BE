package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * DTO trả về thông tin tóm tắt của Warehouse (dùng trong danh sách / phân trang).
 */
import java.util.UUID;

@Getter
@Builder
public class WarehouseResponse {

    private UUID id;
    private String name;
    private String address;
    private String description;
    private BigDecimal capacity;
    private BigDecimal pricePerMonth;
    private String status;
    private String rejectReason;
    private boolean isVerified;

    /** Thông tin loại kho */
    private java.util.UUID typeId;
    private String typeName;

    /** Thông tin chủ kho (summary) */
    private UUID ownerId;
    private String ownerName;
    private String ownerPhone;

    /** Ảnh bìa (ảnh đầu tiên, displayOrder = 0) */
    private String coverImageUrl;

    /** Toàn bộ ảnh — dùng cho detail view */
    private List<String> imageUrls;

    /** Phiên bản cam kết ràng buộc */
    private UUID policyId;
    private String policyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
