package fu.stockspace.stockspace_be.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatsResponse {
    private int year;
    private BigDecimal totalRevenue;
    private BigDecimal listingFeeRevenue;
    private BigDecimal servicePackageRevenue;
    private List<MonthlyRevenueDto> monthlyRevenue;
}
