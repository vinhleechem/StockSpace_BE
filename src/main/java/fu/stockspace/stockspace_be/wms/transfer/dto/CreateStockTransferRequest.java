package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

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

    /** Optional staff responsible for picking at the source warehouse. */
    private UUID sourceStaffId;

    /** Optional staff responsible for receiving at the destination warehouse. */
    private UUID destinationStaffId;

    /** Optional SLA supplied by the operator; omitted values default to 48 hours. */
    @Future(message = "Expected arrival phải ở tương lai")
    private LocalDateTime expectedArrivalAt;

    private String note;

    @NotEmpty(message = "Danh sách hàng hóa chuyển kho không được để trống")
    @Valid
    private List<StockTransferItemRequest> items;
}
