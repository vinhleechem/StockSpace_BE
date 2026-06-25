package fu.stockspace.stockspace_be.wms.product.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSkuResponse {
    private UUID id;
    private UUID tenantId; // null if system recommended SKU
    private UUID categoryId;
    private String categoryName;
    private String skuCode;
    private String name;
    private String unit;
    private Map<String, Object> specifications;
}
