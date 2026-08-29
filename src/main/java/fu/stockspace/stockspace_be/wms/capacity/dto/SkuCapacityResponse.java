package fu.stockspace.stockspace_be.wms.capacity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuCapacityResponse {
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private long quantity;
    private BigDecimal weightKg;
    private BigDecimal volumeM3;
}
