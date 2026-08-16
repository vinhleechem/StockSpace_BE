package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the active warehouse requested by the UI to a display-safe context.
 * A caller-supplied id is never added to the prompt until its role-specific
 * access has been verified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveWarehouseContextResolver {

    private final WarehouseRepository warehouseRepository;
    private final RentalContractRepository contractRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;

    public ChatRequestContext resolve(UUID userId, String roleName, UUID requestedWarehouseId) {
        String normalizedRole = roleName == null
                ? "GUEST"
                : roleName.trim().toUpperCase(Locale.ROOT);
        if (userId == null || requestedWarehouseId == null) {
            return withoutWarehouse(userId, normalizedRole);
        }

        try {
            Optional<Warehouse> warehouse = switch (normalizedRole) {
                case "ROLE_OWNER" -> warehouseRepository.findByIdAndOwnerId(
                        requestedWarehouseId, userId);
                case "ROLE_TENANT" -> resolveTenantWarehouse(userId, requestedWarehouseId);
                case "ROLE_STAFF" -> resolveStaffWarehouse(userId, requestedWarehouseId);
                default -> Optional.empty();
            };
            return warehouse
                    .map(value -> new ChatRequestContext(
                            userId, normalizedRole, value.getId(), value.getName()))
                    .orElseGet(() -> withoutWarehouse(userId, normalizedRole));
        } catch (RuntimeException exception) {
            log.warn("[ActiveWarehouseContext] Resolution failed role={} cause={}",
                    normalizedRole, exception.getClass().getSimpleName());
            return withoutWarehouse(userId, normalizedRole);
        }
    }

    private Optional<Warehouse> resolveTenantWarehouse(UUID tenantId, UUID warehouseId) {
        if (!contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId)) {
            return Optional.empty();
        }
        return warehouseRepository.findById(warehouseId);
    }

    private Optional<Warehouse> resolveStaffWarehouse(UUID staffId, UUID warehouseId) {
        Optional<TenantMember> member = tenantMemberRepository
                .findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId);
        if (member.isEmpty()) {
            return Optional.empty();
        }

        UUID tenantId = member.get().getTenant().getId();
        boolean assigned = assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE);
        boolean hasActiveContract = contractRepository
                .existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId);
        if (!assigned || !hasActiveContract) {
            return Optional.empty();
        }
        return warehouseRepository.findById(warehouseId);
    }

    private ChatRequestContext withoutWarehouse(UUID userId, String roleName) {
        return new ChatRequestContext(userId, roleName, null, null);
    }
}
