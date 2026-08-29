package fu.stockspace.stockspace_be.wms.receipt.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptItemResponse {
    private UUID id;
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private int quantity;
    private UUID rackId;
    private String rackName;
    private UUID binId;
    private String binName;
    private String note;
}
