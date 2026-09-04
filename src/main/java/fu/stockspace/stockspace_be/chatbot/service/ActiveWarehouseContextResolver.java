package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the active warehouse requested by the UI to a display-safe context.
 * A caller-supplied id is never added to the prompt until tenant access has
 * been verified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveWarehouseContextResolver {

    private final WarehouseRepository warehouseRepository;
    private final TenantWarehouseAccessService accessService;

    public ChatRequestContext resolve(UUID tenantId, UUID requestedWarehouseId) {
        if (tenantId == null || requestedWarehouseId == null) {
            return withoutWarehouse(tenantId);
        }

        try {
            Optional<Warehouse> warehouse = resolveTenantWarehouse(
                    tenantId, requestedWarehouseId);
            return warehouse
                    .map(value -> new ChatRequestContext(
                            tenantId, value.getId(), value.getName()))
                    .orElseGet(() -> withoutWarehouse(tenantId));
        } catch (RuntimeException exception) {
            log.warn("[ActiveWarehouseContext] Tenant resolution failed cause={}",
                    exception.getClass().getSimpleName());
            return withoutWarehouse(tenantId);
        }
    }

    private Optional<Warehouse> resolveTenantWarehouse(UUID tenantId, UUID warehouseId) {
        if (!accessService.canObserveWarehouse(tenantId, warehouseId)) {
            return Optional.empty();
        }
        return warehouseRepository.findById(warehouseId);
    }

    private ChatRequestContext withoutWarehouse(UUID tenantId) {
        return new ChatRequestContext(tenantId, null, null);
    }
}
