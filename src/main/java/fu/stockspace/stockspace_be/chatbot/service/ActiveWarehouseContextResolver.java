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
        if (tenantId == null) {
            return withoutWarehouse(null);
        }

        try {
            if (requestedWarehouseId != null) {
                Optional<Warehouse> warehouse = resolveTenantWarehouse(
                        tenantId, requestedWarehouseId);
                if (warehouse.isPresent()) {
                    Warehouse value = warehouse.get();
                    return new ChatRequestContext(tenantId, value.getId(), value.getName());
                }
            }

            // Smart fallback: If no warehouse was explicitly requested, but tenant has exactly 1 active warehouse, auto-resolve it
            java.util.List<Warehouse> activeWarehouses = accessService.findActiveContractWarehouses(tenantId);
            if (activeWarehouses != null && activeWarehouses.size() == 1) {
                Warehouse single = activeWarehouses.get(0);
                log.info("[ActiveWarehouseContext] Auto-resolved single active warehouse: id={} name={}",
                        single.getId(), single.getName());
                return new ChatRequestContext(tenantId, single.getId(), single.getName());
            }

            return withoutWarehouse(tenantId);
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
