package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinSaveRequest {
    private UUID id; // null nếu tạo mới

    @NotBlank(message = "Tên Bin không được để trống")
    private String name;

    @NotBlank(message = "Mã Bin không được để trống")
    private String code;

    private BigDecimal maxWeight;
    private BigDecimal maxVolume;

    @NotNull(message = "Tọa độ X của Bin không được để trống")
    private Integer coordinateX;

    @NotNull(message = "Tọa độ Y của Bin không được để trống")
    private Integer coordinateY;

    @NotNull(message = "Chiều rộng của Bin không được để trống")
    private Integer width;

    @NotNull(message = "Chiều cao của Bin không được để trống")
    private Integer height;
}
