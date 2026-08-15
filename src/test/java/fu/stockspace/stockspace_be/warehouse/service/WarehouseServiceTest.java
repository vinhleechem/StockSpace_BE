package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WarehouseService warehouseService;

    private UUID warehouseId;
    private UUID ownerId;
    private Warehouse warehouse;
    private User owner;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        owner = User.builder()
                .id(ownerId)
                .fullName("Owner Test")
                .phone("0987654321")
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho Test")
                .status(WarehouseStatus.PENDING_APPROVAL)
                .owner(owner)
                .images(new ArrayList<>())
                .build();
    }

    @Test
    void rejectWarehouse_WithReason_Success() {
        String reason = "Kho không đủ giấy phép PCCC";
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.rejectWarehouse(warehouseId, reason);

        assertNotNull(response);
        assertEquals(WarehouseStatus.INACTIVE.name(), response.getStatus());
        assertEquals(reason, response.getRejectReason());

        verify(notificationService).push(
                eq(ownerId),
                eq("Bài đăng kho bãi không được duyệt"),
                contains(reason),
                eq("SYSTEM")
        );
    }

    @Test
    void verifyWarehouse_ClearsRejectReason_Success() {
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        warehouse.setRejectReason("Lý do cũ");

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.verifyWarehouse(warehouseId);

        assertNotNull(response);
        assertEquals(WarehouseStatus.AVAILABLE.name(), response.getStatus());
        assertNull(response.getRejectReason());
    }
}
