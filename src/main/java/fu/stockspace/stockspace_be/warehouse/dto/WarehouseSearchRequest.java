package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;




@Getter
@Setter
public class WarehouseSearchRequest {


    private String keyword;


    private WarehouseStatus status;


    private BigDecimal minRentalPrice;


    private BigDecimal maxRentalPrice;

    /** @deprecated Use minRentalPrice. */
    @Deprecated
    private BigDecimal minPrice;

    /** @deprecated Use maxRentalPrice. */
    @Deprecated
    private BigDecimal maxPrice;

    public BigDecimal getEffectiveMinRentalPrice() {
        return minRentalPrice != null ? minRentalPrice : minPrice;
    }

    public BigDecimal getEffectiveMaxRentalPrice() {
        return maxRentalPrice != null ? maxRentalPrice : maxPrice;
    }


    private BigDecimal minCapacity;




    private Boolean isVerified;
}
