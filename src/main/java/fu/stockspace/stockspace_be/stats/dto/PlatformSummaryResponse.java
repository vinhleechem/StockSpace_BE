package fu.stockspace.stockspace_be.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSummaryResponse {
    private long totalUsers;
    private long totalWarehouses;
    private long totalBookings;
    private long totalContracts;
}
