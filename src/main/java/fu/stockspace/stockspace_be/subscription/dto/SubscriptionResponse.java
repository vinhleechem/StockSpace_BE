package fu.stockspace.stockspace_be.subscription.dto;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {
    private UUID id;
    private UUID tenantId;
    private ServicePackageResponse servicePackage;
    private LocalDate startDate;
    private LocalDate endDate;
    private SubscriptionStatus status;
}