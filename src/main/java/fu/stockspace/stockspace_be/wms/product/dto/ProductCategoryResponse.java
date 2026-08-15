package fu.stockspace.stockspace_be.wms.product.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategoryResponse {
    private UUID id;
    private UUID tenantId;
    private String name;
    private Map<String, Object> defaultAttributes;
}
