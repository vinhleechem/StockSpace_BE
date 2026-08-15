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
public class CreateSkuRequest {

    private UUID categoryId;

    @NotBlank(message = "Mã SKU không được để trống")
    private String skuCode;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @jakarta.validation.constraints.NotNull(message = "Đơn vị tính không được để trống")
    private UUID uomId;

    private Map<String, Object> specifications;
}
