package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkLayoutSaveRequest {
    @NotNull(message = "Chiều rộng lưới layout không được để trống")
    @DecimalMin(value = "0.000001", message = "Layout width must be greater than 0 meters")
    private BigDecimal width;

    @NotNull(message = "Chiều dài lưới layout không được để trống")
    @DecimalMin(value = "0.000001", message = "Layout length must be greater than 0 meters")
    private BigDecimal length;

    @NotNull(message = "Chiều cao lưới layout không được để trống")
    @DecimalMin(value = "0.000001", message = "Layout height must be greater than 0 meters")
    private BigDecimal height;

    @Valid
    private List<RackSaveRequest> racks;


    private List<String> positions;
}
