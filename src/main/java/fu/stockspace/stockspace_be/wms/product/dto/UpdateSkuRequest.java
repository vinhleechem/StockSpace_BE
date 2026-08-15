package fu.stockspace.stockspace_be.wms.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSkuRequest {

    private UUID categoryId;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @jakarta.validation.constraints.NotNull(message = "Đơn vị tính không được để trống")
    private UUID uomId;

    @NotNull(message = "unitWeightKg is required")
    @DecimalMin(value = "0.000001", message = "unitWeightKg must be greater than 0")
    private BigDecimal unitWeightKg;

    @NotNull(message = "unitVolumeM3 is required")
    @DecimalMin(value = "0.000001", message = "unitVolumeM3 must be greater than 0")
    private BigDecimal unitVolumeM3;

    private Map<String, Object> specifications;
}
