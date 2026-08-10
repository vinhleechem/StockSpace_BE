package fu.stockspace.stockspace_be.staff.dto;

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

    private String customTitle;

    private String notes;
}
