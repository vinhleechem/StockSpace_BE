package fu.stockspace.stockspace_be.wms.receipt.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptItemRequest {

    @NotNull(message = "Mã SKU không được để trống")
    private UUID skuId;

    @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
    private int quantity;

    private UUID rackId;

    private UUID binId;

    private String note;
}
