package fu.stockspace.stockspace_be.wallet.dto;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawResponse {
    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private ApprovalStatus status;
    private String adminNotes;
    private UUID transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
