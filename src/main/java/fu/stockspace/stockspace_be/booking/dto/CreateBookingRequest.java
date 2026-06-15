package fu.stockspace.stockspace_be.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO khi Tenant gửi yêu cầu thuê kho.
 */
@Getter
@Setter
public class CreateBookingRequest {

    @NotNull(message = "ID kho không được để trống")
    private UUID warehouseId;

    @NotNull(message = "Số tiền đặt cọc không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Số tiền đặt cọc phải lớn hơn 0")
    private BigDecimal depositAmount;
}
