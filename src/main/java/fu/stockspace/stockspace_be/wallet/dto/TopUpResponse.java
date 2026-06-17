package fu.stockspace.stockspace_be.wallet.dto;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopUpResponse {
    private UUID transactionId;
    private String paymentCode;
    private BigDecimal amount;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String qrCodeUrl;
}
