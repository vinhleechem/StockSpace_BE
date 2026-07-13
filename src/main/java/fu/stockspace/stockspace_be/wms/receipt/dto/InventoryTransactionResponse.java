package fu.stockspace.stockspace_be.wms.receipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionResponse {
    private UUID id;
    private UUID receiptId;
    private UUID batchId;
    private String skuCode;
    private String skuName;
    private int quantityChanged;
    private LocalDateTime createdAt;
}
