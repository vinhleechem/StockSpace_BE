package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * DTO trả về thông tin loại kho.
 */
@Getter
@Builder
public class WarehouseTypeResponse {
    private Integer id;
    private String name;
    private String description;
}
