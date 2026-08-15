package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;





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


    private java.util.UUID typeId;
    private String typeName;


    private UUID ownerId;
    private String ownerName;
    private String ownerPhone;


    private String coverImageUrl;


    private List<String> imageUrls;


    private UUID policyId;
    private String policyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
