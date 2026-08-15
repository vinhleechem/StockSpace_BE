package fu.stockspace.stockspace_be.staff.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffWorkHistoryResponse {

    private UUID staffId;
    private String fullName;
    private String email;
    private String phone;


    private List<TenantTenureResponse> tenantTenures;


    private List<StaffAssignmentResponse> warehouseAssignments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantTenureResponse {
        private UUID membershipId;
        private UUID tenantId;
        private String tenantName;
        private String tenantEmail;
        private LocalDateTime joinedAt;
        private LocalDateTime resignedAt;
        private boolean isActive;
    }
}
