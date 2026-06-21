package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.*;
import fu.stockspace.stockspace_be.warehouse.repository.*;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseLayoutServiceTest {

    @Mock
    private WarehouseLayoutRepository layoutRepository;
    @Mock
    private WarehouseZoneRepository zoneRepository;
    @Mock
    private WarehouseRackRepository rackRepository;
    @Mock
    private WarehouseBinRepository binRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RentalContractRepository contractRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;

    @InjectMocks
    private WarehouseLayoutService layoutService;

    private UUID warehouseId;
    private UUID userId;
    private Warehouse warehouse;
    private WarehouseLayout defaultLayout;
    private User owner;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        userId = UUID.randomUUID();

        owner = User.builder().id(userId).email("owner@test.com").build();
        warehouse = Warehouse.builder().id(warehouseId).owner(owner).name("Main Warehouse").build();
        defaultLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(warehouse).isDefault(true).width(100).height(100).build();
    }

    @Test
    void testGetLayoutTree_Success() {
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));

        WarehouseZone zone = WarehouseZone.builder().id(UUID.randomUUID()).layout(defaultLayout).name("Zone A").coordinateX(0).coordinateY(0).width(50).height(50).build();
        WarehouseRack rack = WarehouseRack.builder().id(UUID.randomUUID()).zone(zone).name("Rack A1").coordinateX(0).coordinateY(0).width(10).height(10).build();
        WarehouseBin bin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack).name("Bin A1-1").coordinateX(0).coordinateY(0).width(2).height(2).build();

        when(zoneRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(zone));
        when(rackRepository.findAllByZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        WarehouseLayoutResponse response = layoutService.getLayoutTree(warehouseId, userId, "OWNER");

        assertNotNull(response);
        assertEquals(defaultLayout.getId(), response.getId());
        assertEquals(1, response.getZones().size());
        assertEquals("Zone A", response.getZones().get(0).getName());
        assertEquals("Rack A1", response.getZones().get(0).getRacks().get(0).getName());
        assertEquals("Bin A1-1", response.getZones().get(0).getRacks().get(0).getBins().get(0).getName());
    }

    @Test
    void testCloneLayout_Success() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = User.builder().id(tenantId).email("tenant@test.com").build();

        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)).thenReturn(Optional.empty());
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));

        WarehouseLayout clonedLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(warehouse).tenant(tenantUser).isDefault(false).width(100).height(100).build();
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(clonedLayout);

        WarehouseZone zone = WarehouseZone.builder().id(UUID.randomUUID()).layout(defaultLayout).name("Zone A").coordinateX(0).coordinateY(0).width(50).height(50).build();
        WarehouseRack rack = WarehouseRack.builder().id(UUID.randomUUID()).zone(zone).name("Rack A1").coordinateX(0).coordinateY(0).width(10).height(10).build();
        WarehouseBin bin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack).name("Bin A1-1").coordinateX(0).coordinateY(0).width(2).height(2).build();

        when(zoneRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(zone));
        when(rackRepository.findAllByZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        when(zoneRepository.save(any(WarehouseZone.class))).thenAnswer(i -> i.getArguments()[0]);
        when(rackRepository.save(any(WarehouseRack.class))).thenAnswer(i -> i.getArguments()[0]);
        when(binRepository.save(any(WarehouseBin.class))).thenAnswer(i -> i.getArguments()[0]);

        assertDoesNotThrow(() -> layoutService.cloneLayout(warehouseId, tenantId));

        verify(layoutRepository, times(1)).save(any(WarehouseLayout.class));
        verify(zoneRepository, times(1)).save(any(WarehouseZone.class));
        verify(rackRepository, times(1)).save(any(WarehouseRack.class));
        verify(binRepository, times(1)).save(any(WarehouseBin.class));
    }

    @Test
    void testSaveLayoutBulk_CoordinateOutOfBounds_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        // Zone width + coordinateX = 110 > Layout width (100)
        ZoneSaveRequest zoneReq = ZoneSaveRequest.builder()
                .name("Zone Out")
                .code("Z_OUT")
                .coordinateX(60)
                .coordinateY(10)
                .width(50)
                .height(20)
                .build();

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(100)
                .height(100)
                .zones(List.of(zoneReq))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));

        assertEquals(ErrorCode.LAYOUT_INVALID_COORDINATES, ErrorCode.LAYOUT_INVALID_COORDINATES);
        assertTrue(ex.getMessage().contains("vượt quá biên giới hạn"));
    }

    @Test
    void testSaveLayoutBulk_DeleteNonEmptyBin_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        // Giả sử DB đang có 1 zone, 1 rack, 1 bin
        UUID zoneId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        WarehouseZone zone = WarehouseZone.builder().id(zoneId).layout(defaultLayout).name("Zone A").coordinateX(0).coordinateY(0).width(50).height(50).build();
        WarehouseRack rack = WarehouseRack.builder().id(rackId).zone(zone).name("Rack A1").coordinateX(0).coordinateY(0).width(10).height(10).build();
        WarehouseBin bin = WarehouseBin.builder().id(binId).rack(rack).name("Bin A1-1").coordinateX(0).coordinateY(0).width(2).height(2).build();

        when(zoneRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(zone));
        when(rackRepository.findAllByZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackZoneLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        // Request gửi lên không có bin này (yêu cầu xoá)
        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(100)
                .height(100)
                .zones(Collections.emptyList()) // Xoá hết
                .build();

        // Giả lập tồn kho trong bin còn hàng (quantity = 5)
        when(stockBatchRepository.existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(binId, 0)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));

        assertEquals("Không thể xóa ô chứa Bin A1-1 vì vẫn còn hàng tồn kho", ex.getMessage());
        verify(binRepository, never()).deleteById(any());
    }
}
