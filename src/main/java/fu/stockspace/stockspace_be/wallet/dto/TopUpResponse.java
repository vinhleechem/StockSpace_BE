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
    private String paymentUrl;
    private BigDecimal amount;
}
