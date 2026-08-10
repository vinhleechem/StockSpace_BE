package fu.stockspace.stockspace_be.stats.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.stats.dto.OccupancyStatsResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.stats.service.OwnerStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/stats")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('OWNER_STATS_READ')")
public class OwnerStatsController {

    private final OwnerStatsService ownerStatsService;

    @GetMapping("/revenue")
    public ResponseEntity<RevenueStatsResponse> getRevenueSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "year", required = false) Integer year) {
        return ResponseEntity.ok(ownerStatsService.getRevenueSummary(user.getId(), year));
    }

    @GetMapping("/occupancy")
    public ResponseEntity<OccupancyStatsResponse> getOccupancyRate(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ownerStatsService.getOccupancyRate(user.getId()));
    }
}
