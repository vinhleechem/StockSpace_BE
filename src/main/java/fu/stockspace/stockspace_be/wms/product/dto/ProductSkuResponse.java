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
    private UUID tenantId;
    private UUID categoryId;
    private String categoryName;
    private String skuCode;
    private String name;
    private UUID uomId;
    private String uomCode;
    private String uomName;
    private Map<String, Object> specifications;
}
