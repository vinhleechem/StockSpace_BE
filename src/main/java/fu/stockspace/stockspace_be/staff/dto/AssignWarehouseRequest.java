package fu.stockspace.stockspace_be.staff.dto;

import fu.stockspace.stockspace_be.staff.entity.WarehouseRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignWarehouseRequest {

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;

    @NotNull(message = "role (quyền WMS tại kho) không được để trống")
    private WarehouseRole role;

    private String customTitle;

    private String notes;
}
