package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;




@Getter
@Builder
public class WarehouseTypeResponse {
    private java.util.UUID id;
    private String name;
    private String description;
}
