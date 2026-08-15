package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinSaveRequest {
    private UUID id;

    private Integer shelfLevel;

    @NotBlank(message = "Tên Bin không được để trống")
    private String name;

    @NotBlank(message = "Mã Bin không được để trống")
    private String code;

    private BigDecimal maxWeight;
    private BigDecimal maxVolume;

    @NotNull(message = "Tọa độ X của Bin không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Bin coordinateX must be non-negative")
    private BigDecimal coordinateX;

    @NotNull(message = "Tọa độ Y của Bin không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Bin coordinateY must be non-negative")
    private BigDecimal coordinateY;

    @DecimalMin(value = "0.0", inclusive = true, message = "Bin positionZ must be non-negative")
    private BigDecimal positionZ;

    @NotNull(message = "Chiều rộng của Bin không được để trống")
    @DecimalMin(value = "0.000001", message = "Bin width must be greater than 0")
    private BigDecimal width;

    @DecimalMin(value = "0.000001", message = "Bin length must be greater than 0")
    @NotNull(message = "Bin length is required")
    @DecimalMin(value = "0.000001", message = "Bin length must be greater than 0")
    private BigDecimal length;

    @NotNull(message = "Chiều cao của Bin không được để trống")
    @DecimalMin(value = "0.000001", message = "Bin height must be greater than 0")
    private BigDecimal height;
}
