package fu.stockspace.stockspace_be.wallet.dto;
import fu.stockspace.stockspace_be.wallet.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpRequest {
    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "1000.00", message = "Số tiền nạp tối thiểu là 1,000 VND")
    private BigDecimal amount;
    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;
}
