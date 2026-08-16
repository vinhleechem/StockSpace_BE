package fu.stockspace.stockspace_be.wallet.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawRequestDto {
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "50000.00", message = "Số tiền rút tối thiểu là 50,000 VND")
    @DecimalMax(value = "9999999999999.99", message = "Amount exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Amount must have at most 13 integer digits and 2 decimal places")
    private BigDecimal amount;
    @NotBlank(message = "Tên ngân hàng không được để trống")
    private String bankName;
    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    private String bankAccountNumber;
    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    private String bankAccountHolder;
}
