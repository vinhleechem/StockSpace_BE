package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStockTransferRequest {

    @NotNull(message = "Kho nguồn không được để trống")
    private UUID sourceWarehouseId;

    @NotNull(message = "Kho đích không được để trống")
    private UUID destinationWarehouseId;

    private String note;

    @NotEmpty(message = "Danh sách hàng hóa chuyển kho không được để trống")
    @Valid
    private List<StockTransferItemRequest> items;
}
