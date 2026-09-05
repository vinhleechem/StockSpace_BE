package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditLock;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditLockRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAuditLockServiceTest {
    @Mock private InventoryAuditLockRepository lockRepository;
    @Mock private WarehouseRepository warehouseRepository;

    @InjectMocks private InventoryAuditLockService lockService;

    @Test
    void acquireRejectsWarehouseWithActiveLock() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).build();
        InventoryAudit audit = InventoryAudit.builder().warehouse(warehouse).build();
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(lockRepository.findActiveForUpdate(warehouseId))
                .thenReturn(Optional.of(InventoryAuditLock.builder().warehouse(warehouse).build()));

        assertThrows(ResourceConflictException.class, () -> lockService.acquire(audit));
        verify(lockRepository, never()).saveAndFlush(any());
    }

    @Test
    void movementGuardRejectsLockedWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByIdForUpdate(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(lockRepository.findActive(warehouseId))
                .thenReturn(Optional.of(InventoryAuditLock.builder().build()));

        assertThrows(ResourceConflictException.class, () -> lockService.assertMovementAllowed(warehouseId));
    }
}
