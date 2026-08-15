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


    private BigDecimal minPrice;


    private BigDecimal maxPrice;


    private BigDecimal minCapacity;




    private Boolean isVerified;
}
