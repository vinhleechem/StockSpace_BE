package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Locale;
import java.util.Set;
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

    private static final Set<String> ALLOWED_SCREENS = Set.of(
            "dashboard", "contracts", "warehouse", "inventory", "receipt",
            "audit", "transfer", "wallet", "subscription", "notifications"
    );

    private final WarehouseRepository warehouseRepository;
    private final TenantWarehouseAccessService accessService;

    public ChatRequestContext resolve(UUID tenantId, UUID requestedWarehouseId) {
        return resolve(tenantId, requestedWarehouseId, null);
    }

    public ChatRequestContext resolve(UUID tenantId,
                                      UUID requestedWarehouseId,
                                      String requestedScreen) {
        String activeScreen = normalizeScreen(requestedScreen);
        if (tenantId == null) {
            return withoutWarehouse(null);
        }

        try {
            if (requestedWarehouseId != null) {
                Optional<Warehouse> warehouse = resolveTenantWarehouse(
                        tenantId, requestedWarehouseId);
                if (warehouse.isPresent()) {
                    Warehouse value = warehouse.get();
                    return new ChatRequestContext(tenantId, value.getId(), value.getName(), activeScreen);
                }
            }

            // Smart fallback: If no warehouse was explicitly requested, but tenant has exactly 1 active warehouse, auto-resolve it
            java.util.List<Warehouse> activeWarehouses = accessService.findActiveContractWarehouses(tenantId);
            if (activeWarehouses != null && activeWarehouses.size() == 1) {
                Warehouse single = activeWarehouses.get(0);
                log.info("[ActiveWarehouseContext] Auto-resolved single active warehouse: id={} name={}",
                        single.getId(), single.getName());
                return new ChatRequestContext(tenantId, single.getId(), single.getName(), activeScreen);
            }

            return withoutWarehouse(tenantId, activeScreen);
        } catch (RuntimeException exception) {
            log.warn("[ActiveWarehouseContext] Tenant resolution failed cause={}",
                    exception.getClass().getSimpleName());
            return withoutWarehouse(tenantId, activeScreen);
        }
    }

    private Optional<Warehouse> resolveTenantWarehouse(UUID tenantId, UUID warehouseId) {
        if (!accessService.canObserveWarehouse(tenantId, warehouseId)) {
            return Optional.empty();
        }
        return warehouseRepository.findById(warehouseId);
    }

    private ChatRequestContext withoutWarehouse(UUID tenantId) {
        return withoutWarehouse(tenantId, null);
    }

    private ChatRequestContext withoutWarehouse(UUID tenantId, String activeScreen) {
        return new ChatRequestContext(tenantId, null, null, activeScreen);
    }

    private String normalizeScreen(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_SCREENS.contains(normalized) ? normalized : null;
    }
}
