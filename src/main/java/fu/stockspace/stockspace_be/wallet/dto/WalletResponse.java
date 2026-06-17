package fu.stockspace.stockspace_be.wallet.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {
    private UUID id;
    private Long userId;
    private BigDecimal balance;
    private LocalDateTime updatedAt;
}
