package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneSaveRequest {
    private UUID id; // null nếu tạo mới

    @NotBlank(message = "Tên Zone không được để trống")
    private String name;

    @NotBlank(message = "Mã Zone không được để trống")
    private String code;

    private BigDecimal maxWeight;
    private BigDecimal maxVolume;

    @NotNull(message = "Tọa độ X của Zone không được để trống")
    private Integer coordinateX;

    @NotNull(message = "Tọa độ Y của Zone không được để trống")
    private Integer coordinateY;

    @NotNull(message = "Chiều rộng của Zone không được để trống")
    private Integer width;

    @NotNull(message = "Chiều cao của Zone không được để trống")
    private Integer height;

    @Valid
    private List<RackSaveRequest> racks;
}
