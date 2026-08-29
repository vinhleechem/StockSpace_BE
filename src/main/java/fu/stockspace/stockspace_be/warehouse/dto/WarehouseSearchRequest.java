package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;




@Getter
@Setter
public class WarehouseSearchRequest {


    private String keyword;


    private WarehouseStatus status;


    private BigDecimal minRentalPrice;


    private BigDecimal maxRentalPrice;

    /** @deprecated Use minRentalPrice. Kept for one compatibility release. */
    @Deprecated
    @Schema(deprecated = true, description = "Legacy alias for minRentalPrice")
    private BigDecimal minPrice;

    /** @deprecated Use maxRentalPrice. Kept for one compatibility release. */
    @Deprecated
    @Schema(deprecated = true, description = "Legacy alias for maxRentalPrice")
    private BigDecimal maxPrice;

    public BigDecimal getEffectiveMinRentalPrice() {
        return minRentalPrice != null ? minRentalPrice : minPrice;
    }

    public BigDecimal getEffectiveMaxRentalPrice() {
        return maxRentalPrice != null ? maxRentalPrice : maxPrice;
    }


    private BigDecimal minCapacity;

    private BigDecimal maxCapacity;

    private String provinceCode;

    private String districtCode;

    private UUID warehouseTypeId;



    private Boolean isVerified;
}
