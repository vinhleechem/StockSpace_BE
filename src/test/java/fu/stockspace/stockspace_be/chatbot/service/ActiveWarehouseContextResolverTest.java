package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActiveWarehouseContextResolverTest {

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
    private final TenantWarehouseAccessService accessService = mock(TenantWarehouseAccessService.class);
    private final ActiveWarehouseContextResolver resolver = new ActiveWarehouseContextResolver(
            warehouseRepository, accessService);

    @Test
    void resolvesTenantContextOnlyAfterAccessCheck() {
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Bình Tân").build();
        when(accessService.canObserveWarehouse(tenantId, warehouseId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        ChatRequestContext context = resolver.resolve(tenantId, warehouseId);

        assertEquals(warehouseId, context.activeWarehouseId());
        assertEquals("Kho Bình Tân", context.activeWarehouseName());
    }

    @Test
    void doesNotExposeTenantWarehouseNameWithoutAnActiveContract() {
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(accessService.canObserveWarehouse(tenantId, warehouseId))
                .thenReturn(false);

        ChatRequestContext context = resolver.resolve(tenantId, warehouseId);

        assertNull(context.activeWarehouseId());
        assertNull(context.activeWarehouseName());
        verifyNoInteractions(warehouseRepository);
    }

}
