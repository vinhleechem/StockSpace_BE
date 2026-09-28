package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReceiptDisposition;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferDestinationAllocationRequest {

    @NotNull(message = "Transfer item không được để trống")
    private UUID itemId;

    @NotNull(message = "Rack đích không được để trống")
    private UUID destinationRackId;

    @NotNull(message = "Bin đích không được để trống")
    private UUID destinationBinId;

    @Min(value = 1, message = "Số lượng phân bổ phải lớn hơn 0")
    private int quantity;

    @Builder.Default
    private StockTransferReceiptDisposition disposition = StockTransferReceiptDisposition.GOOD;

    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự")
    private String note;
}
