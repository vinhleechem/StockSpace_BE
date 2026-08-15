package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RackSaveRequest {
    private UUID id;


    @NotBlank(message = "Tên Rack không được để trống")
    private String name;

    @NotBlank(message = "Mã Rack không được để trống")
    private String code;

    private BigDecimal maxWeight;
    private BigDecimal maxVolume;

    @NotNull(message = "Tọa độ X của Rack không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rack coordinateX must be non-negative")
    private BigDecimal coordinateX;

    @NotNull(message = "Tọa độ Y của Rack không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rack coordinateY must be non-negative")
    private BigDecimal coordinateY;

    @DecimalMin(value = "0.0", inclusive = true, message = "Rack positionZ must be non-negative")
    private BigDecimal positionZ;
    private Integer rotation;

    @NotNull(message = "Chiều rộng của Rack không được để trống")
    @DecimalMin(value = "0.000001", message = "Rack width must be greater than 0")
    private BigDecimal width;

    @DecimalMin(value = "0.000001", message = "Rack length must be greater than 0")
    @NotNull(message = "Rack length is required")
    @DecimalMin(value = "0.000001", message = "Rack length must be greater than 0")
    private BigDecimal length;

    @NotNull(message = "Chiều cao của Rack không được để trống")
    @DecimalMin(value = "0.000001", message = "Rack height must be greater than 0")
    private BigDecimal height;

    @Valid
    private List<BinSaveRequest> bins;
}
