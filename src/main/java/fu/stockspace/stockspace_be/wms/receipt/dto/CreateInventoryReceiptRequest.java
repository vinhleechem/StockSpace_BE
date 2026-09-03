package fu.stockspace.stockspace_be.wms.receipt.dto;

import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInventoryReceiptRequest {

    @NotNull(message = "Mã kho không được để trống")
    private UUID warehouseId;

    @NotNull(message = "Loại phiếu không được để trống")
    private DocumentType type;

    private String signatureData;

    @Size(max = 255, message = "Tên nơi gửi không được vượt quá 255 ký tự")
    private String senderName;

    @Size(max = 255, message = "Tên nơi nhận không được vượt quá 255 ký tự")
    private String receiverName;

    @NotEmpty(message = "Danh sách chi tiết hàng hóa không được để trống")
    @Valid
    private List<ReceiptItemRequest> items;
}
