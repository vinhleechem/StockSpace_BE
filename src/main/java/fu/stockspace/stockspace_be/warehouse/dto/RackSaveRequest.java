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
public class RackSaveRequest {
    private UUID id;


    @NotBlank(message = "Tên Rack không được để trống")
    private String name;

    @NotBlank(message = "Mã Rack không được để trống")
    private String code;

    private BigDecimal maxWeight;
    private BigDecimal maxVolume;

    @NotNull(message = "Tọa độ X của Rack không được để trống")
    private Integer coordinateX;

    @NotNull(message = "Tọa độ Y của Rack không được để trống")
    private Integer coordinateY;

    private Integer positionZ;
    private Integer rotation;

    @NotNull(message = "Chiều rộng của Rack không được để trống")
    private Integer width;

    private Integer length;

    @NotNull(message = "Chiều cao của Rack không được để trống")
    private Integer height;

    @Valid
    private List<BinSaveRequest> bins;
}
