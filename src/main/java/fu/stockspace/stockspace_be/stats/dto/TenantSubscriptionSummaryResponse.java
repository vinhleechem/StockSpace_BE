package fu.stockspace.stockspace_be.stats.dto;

import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscriptionSummaryResponse {
    private UUID id;
    private String packageName;
    private LocalDate startDate;
    private LocalDate endDate;
    private SubscriptionStatus status;
}
