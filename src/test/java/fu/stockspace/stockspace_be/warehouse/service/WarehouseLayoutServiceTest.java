package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseLayoutServiceTest {

    @Mock
    private WarehouseLayoutRepository layoutRepository;
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
        defaultLayout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .isDefault(true)
                .width(100)
                .length(100)
                .height(10)
                .build();
    }

    @Test
    void testGetLayoutTree_Success() {
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));

        WarehouseRack rack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(defaultLayout)
                .zoneName("Khu A")
                .name("Rack A1")
                .coordinateX(0)
                .coordinateY(0)
                .width(10)
                .length(10)
                .height(5)
                .build();

        WarehouseBin bin = WarehouseBin.builder()
                .id(UUID.randomUUID())
                .rack(rack)
                .name("Bin A1-1")
                .coordinateX(0)
                .coordinateY(0)
                .width(2)
                .length(2)
                .height(1)
                .build();

        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));
        when(stockBatchRepository.existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(bin.getId(), 0)).thenReturn(false);

        WarehouseLayoutResponse response = layoutService.getLayoutTree(warehouseId, userId, "OWNER");

        assertNotNull(response);
        assertEquals(defaultLayout.getId(), response.getId());
        assertEquals(1, response.getTotalRacks());
        assertEquals(1, response.getTotalBins());
        assertEquals(0, response.getOccupiedBins());
        assertEquals(1, response.getEmptyBins());
        assertEquals("Rack A1", response.getRacks().get(0).getName());
        assertEquals("Bin A1-1", response.getRacks().get(0).getBins().get(0).getName());
    }

    @Test
    void testCloneLayout_Success() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = User.builder().id(tenantId).email("tenant@test.com").build();

        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)).thenReturn(Optional.empty());
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));

        WarehouseLayout clonedLayout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .tenant(tenantUser)
                .isDefault(false)
                .width(100)
                .length(100)
                .height(10)
                .build();
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(clonedLayout);

        WarehouseRack rack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(defaultLayout)
                .zoneName("Khu A")
                .name("Rack A1")
                .coordinateX(0)
                .coordinateY(0)
                .width(10)
                .length(10)
                .height(5)
                .build();

        WarehouseBin bin = WarehouseBin.builder()
                .id(UUID.randomUUID())
                .rack(rack)
                .name("Bin A1-1")
                .coordinateX(0)
                .coordinateY(0)
                .width(2)
                .length(2)
                .height(1)
                .build();

        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        when(rackRepository.save(any(WarehouseRack.class))).thenAnswer(i -> i.getArguments()[0]);
        when(binRepository.save(any(WarehouseBin.class))).thenAnswer(i -> i.getArguments()[0]);

        assertDoesNotThrow(() -> layoutService.cloneLayout(warehouseId, tenantId));

        verify(layoutRepository, times(1)).save(any(WarehouseLayout.class));
        verify(rackRepository, times(1)).save(any(WarehouseRack.class));
        verify(binRepository, times(1)).save(any(WarehouseBin.class));
    }

    @Test
    void testSaveLayoutBulk_CoordinateOutOfBounds_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        // Rack width + coordinateX = 110 > Layout width (100)
        RackSaveRequest rackReq = RackSaveRequest.builder()
                .name("Rack Out")
                .code("R_OUT")
                .coordinateX(60)
                .coordinateY(10)
                .width(50)
                .length(10)
                .height(5)
                .build();

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(100)
                .length(100)
                .height(10)
                .racks(List.of(rackReq))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));

        assertTrue(ex.getMessage().contains("vượt quá biên giới hạn"));
    }

    @Test
    void testSaveLayoutBulk_DeleteNonEmptyBin_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        WarehouseRack rack = WarehouseRack.builder().id(rackId).layout(defaultLayout).name("Rack A1").coordinateX(0).coordinateY(0).width(10).length(10).height(5).build();
        WarehouseBin bin = WarehouseBin.builder().id(binId).rack(rack).name("Bin A1-1").coordinateX(0).coordinateY(0).width(2).length(2).height(1).build();

        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(100)
                .length(100)
                .height(10)
                .racks(Collections.emptyList()) // Xoá hết
                .build();

        when(stockBatchRepository.existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(binId, 0)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));

        assertEquals("Không thể xóa ô chứa Bin A1-1 vì vẫn còn hàng tồn kho", ex.getMessage());
        verify(binRepository, never()).deleteById(any());
    }

    @Test
    void testSaveLayoutBulk_TenantCannotAddRacks_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(userId, warehouseId)).thenReturn(true);
        WarehouseLayout tenantLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(warehouse).isDefault(false).build();
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId)).thenReturn(Optional.of(tenantLayout));

        when(rackRepository.findAllByLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());

        // Request gửi thêm rack mới (id = null)
        RackSaveRequest newRack = RackSaveRequest.builder().name("New Rack").code("R_NEW").coordinateX(0).coordinateY(0).width(5).length(5).height(3).build();
        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder().width(100).length(100).height(10).racks(List.of(newRack)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "TENANT", request));

        assertEquals("Tenant không được phép thêm kệ hàng mới vào sơ đồ.", ex.getMessage());
    }
}
