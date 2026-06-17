package fu.stockspace.stockspace_be.wallet.dto;
import fu.stockspace.stockspace_be.wallet.entity.PaymentMethod;
import fu.stockspace.stockspace_be.wallet.entity.TransactionStatus;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private UUID id;
    private BigDecimal amount;
    private TransactionType transactionType;
    private PaymentMethod paymentMethod;
    private TransactionStatus status;
    private String paymentCode;
    private String referenceId;
    private UUID bookingId;
    private UUID subscriptionId;
    private LocalDateTime createdAt;
}
