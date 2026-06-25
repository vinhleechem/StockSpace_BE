package fu.stockspace.stockspace_be.wms.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSkuRequest {

    private UUID categoryId; // nullable

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @NotBlank(message = "Đơn vị tính không được để trống")
    private String unit;

    private Map<String, Object> specifications;
}
