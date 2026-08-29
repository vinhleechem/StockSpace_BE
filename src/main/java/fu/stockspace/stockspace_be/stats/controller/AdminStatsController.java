package fu.stockspace.stockspace_be.stats.controller;

import fu.stockspace.stockspace_be.stats.dto.PlatformSummaryResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.stats.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_STATS_READ')")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/summary")
    public ResponseEntity<PlatformSummaryResponse> getPlatformSummary() {
        return ResponseEntity.ok(adminStatsService.getPlatformSummary());
    }

    @GetMapping("/revenue")
    public ResponseEntity<RevenueStatsResponse> getMonthlyRevenue(
            @RequestParam(name = "year", required = false) Integer year) {
        return ResponseEntity.ok(adminStatsService.getMonthlyRevenue(year));
    }
}
