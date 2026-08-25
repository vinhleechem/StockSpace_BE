package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
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
import java.math.BigDecimal;

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
    private TenantWarehouseAccessService tenantWarehouseAccessService;
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
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .build();
    }

    @Test
    void testGetLayoutTree_Success() {
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));

        WarehouseRack rack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(defaultLayout)
                .name("Rack A1")
                .coordinateX(new BigDecimal("0"))
                .coordinateY(new BigDecimal("0"))
                .width(new BigDecimal("10"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        WarehouseBin bin = WarehouseBin.builder()
                .id(UUID.randomUUID())
                .rack(rack)
                .name("Bin A1-1")
                .coordinateX(new BigDecimal("0"))
                .coordinateY(new BigDecimal("0"))
                .width(new BigDecimal("2"))
                .length(new BigDecimal("2"))
                .height(new BigDecimal("1"))
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
        assertNotNull(response.getRacks().get(0).getOccupiedPositions());
        assertEquals(100, response.getRacks().get(0).getOccupiedPositions().size());
        assertTrue(response.getRacks().get(0).getOccupiedPositions().contains("0:0"));
        assertTrue(response.getRacks().get(0).getOccupiedPositions().contains("9:9"));

        assertEquals("Bin A1-1", response.getRacks().get(0).getBins().get(0).getName());
        assertNotNull(response.getRacks().get(0).getBins().get(0).getOccupiedPositions());
        assertEquals(4, response.getRacks().get(0).getBins().get(0).getOccupiedPositions().size());
        assertTrue(response.getRacks().get(0).getBins().get(0).getOccupiedPositions().contains("0:0"));
        assertTrue(response.getRacks().get(0).getBins().get(0).getOccupiedPositions().contains("1:1"));
    }

    @Test
    void testGetLayoutTree_TenantRequiresContractButNotSubscription() {
        User tenant = User.builder().id(userId).email("tenant@test.com").build();
        WarehouseLayout tenantLayout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .tenant(tenant)
                .isDefault(false)
                .width(new BigDecimal("20"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .build();
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(Optional.of(tenantLayout));
        when(rackRepository.findAllByLayoutId(tenantLayout.getId()))
                .thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(tenantLayout.getId()))
                .thenReturn(Collections.emptyList());

        WarehouseLayoutResponse response = layoutService.getLayoutTree(warehouseId, userId, "TENANT");

        assertEquals(tenantLayout.getId(), response.getId());
        verify(tenantWarehouseAccessService).requireActiveContract(userId, warehouseId);
        verify(tenantWarehouseAccessService, never()).requireWmsAccess(userId, warehouseId);
    }

    @Test
    void testGetLayoutTree_PublicRequiresVisibleWarehouse() {
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> layoutService.getLayoutTree(warehouseId, null, "PUBLIC"));
        verify(layoutRepository, never()).findByWarehouseIdAndIsDefaultTrue(warehouseId);
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
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .build();
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(clonedLayout);

        WarehouseRack rack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(defaultLayout)
                .name("Rack A1")
                .coordinateX(new BigDecimal("0"))
                .coordinateY(new BigDecimal("0"))
                .width(new BigDecimal("10"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        WarehouseBin bin = WarehouseBin.builder()
                .id(UUID.randomUUID())
                .rack(rack)
                .name("Bin A1-1")
                .coordinateX(new BigDecimal("0"))
                .coordinateY(new BigDecimal("0"))
                .width(new BigDecimal("2"))
                .length(new BigDecimal("2"))
                .height(new BigDecimal("1"))
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
    void testCloneLayout_RestoresDeletedCloneFromCurrentDefault() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = User.builder().id(tenantId).email("tenant@test.com").build();
        UUID tenantLayoutId = UUID.randomUUID();

        WarehouseLayout deletedTenantLayout = WarehouseLayout.builder()
                .id(tenantLayoutId)
                .warehouse(warehouse)
                .tenant(tenantUser)
                .isDefault(false)
                .isActive(false)
                .isDeleted(true)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        WarehouseRack oldRack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(deletedTenantLayout)
                .name("Old Rack")
                .build();
        WarehouseBin oldBin = WarehouseBin.builder()
                .id(UUID.randomUUID())
                .rack(oldRack)
                .name("Old Bin")
                .build();

        WarehouseRack defaultRack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(defaultLayout)
                .name("Current Rack")
                .coordinateX(BigDecimal.ZERO)
                .coordinateY(BigDecimal.ZERO)
                .width(new BigDecimal("20"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(deletedTenantLayout));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId))
                .thenReturn(Optional.of(defaultLayout));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(binRepository.findAllByRackLayoutId(tenantLayoutId)).thenReturn(List.of(oldBin));
        when(rackRepository.findAllByLayoutId(tenantLayoutId)).thenReturn(List.of(oldRack));
        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(defaultRack));
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(Collections.emptyList());
        when(layoutRepository.save(any(WarehouseLayout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rackRepository.save(any(WarehouseRack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        layoutService.cloneLayout(warehouseId, tenantId);

        assertTrue(deletedTenantLayout.isActive());
        assertFalse(deletedTenantLayout.isDeleted());
        assertEquals(defaultLayout.getWidth(), deletedTenantLayout.getWidth());
        assertEquals(defaultLayout.getLength(), deletedTenantLayout.getLength());
        assertEquals(defaultLayout.getHeight(), deletedTenantLayout.getHeight());
        assertFalse(oldRack.isActive());
        assertTrue(oldRack.isDeleted());
        assertFalse(oldBin.isActive());
        assertTrue(oldBin.isDeleted());
        verify(layoutRepository).save(deletedTenantLayout);
        verify(rackRepository).saveAll(List.of(oldRack));
        verify(binRepository).saveAll(List.of(oldBin));
        verify(rackRepository).save(any(WarehouseRack.class));
    }

    @Test
    void testSaveLayoutBulk_CoordinateOutOfBounds_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);


        RackSaveRequest rackReq = RackSaveRequest.builder()
                .name("Rack Out")
                .code("R_OUT")
                .coordinateX(new BigDecimal("60"))
                .coordinateY(new BigDecimal("10"))
                .width(new BigDecimal("50"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .racks(List.of(rackReq))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));

        assertTrue(ex.getMessage().contains("parent bounds"));
    }

    @Test
    void testSaveLayoutBulk_AllowsDecimalMeters() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);
        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(Collections.emptyList());
        when(rackRepository.save(any(WarehouseRack.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RackSaveRequest rackReq = RackSaveRequest.builder()
                .name("Decimal Rack")
                .code("R_DECIMAL")
                .coordinateX(new BigDecimal("1.25"))
                .coordinateY(new BigDecimal("2.50"))
                .positionZ(new BigDecimal("0.75"))
                .width(new BigDecimal("3.50"))
                .length(new BigDecimal("4.25"))
                .height(new BigDecimal("2.80"))
                .build();

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("20.50"))
                .length(new BigDecimal("30.50"))
                .height(new BigDecimal("10.00"))
                .racks(List.of(rackReq))
                .build();

        assertDoesNotThrow(() -> layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));
        verify(rackRepository).save(argThat(rack ->
                new BigDecimal("1.25").compareTo(rack.getCoordinateX()) == 0
                        && new BigDecimal("3.50").compareTo(rack.getWidth()) == 0));
    }

    @Test
    void testSaveLayoutBulk_DeleteNonEmptyBin_ThrowsException() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        WarehouseRack rack = WarehouseRack.builder().id(rackId).layout(defaultLayout).name("Rack A1").coordinateX(new BigDecimal("0")).coordinateY(new BigDecimal("0")).width(new BigDecimal("10")).length(new BigDecimal("10")).height(new BigDecimal("5")).build();
        WarehouseBin bin = WarehouseBin.builder().id(binId).rack(rack).name("Bin A1-1").coordinateX(new BigDecimal("0")).coordinateY(new BigDecimal("0")).width(new BigDecimal("2")).length(new BigDecimal("2")).height(new BigDecimal("1")).build();

        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(List.of(bin));

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .racks(Collections.emptyList())
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
        doNothing().when(tenantWarehouseAccessService).requireWmsAccess(userId, warehouseId);
        WarehouseLayout tenantLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(warehouse).isDefault(false).build();
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId)).thenReturn(Optional.of(tenantLayout));

        when(rackRepository.findAllByLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());


        RackSaveRequest newRack = RackSaveRequest.builder().name("New Rack").code("R_NEW").coordinateX(new BigDecimal("0")).coordinateY(new BigDecimal("0")).width(new BigDecimal("5")).length(new BigDecimal("5")).height(new BigDecimal("3")).build();
        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder().width(new BigDecimal("100")).length(new BigDecimal("100")).height(new BigDecimal("10")).racks(List.of(newRack)).build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                layoutService.saveLayoutBulk(warehouseId, userId, "TENANT", request));

        verify(tenantWarehouseAccessService).requireWmsAccess(userId, warehouseId);

        assertEquals("Tenant không được phép thêm kệ hàng mới vào sơ đồ.", ex.getMessage());
    }

    @Test
    void testSaveLayoutBulk_OwnerCanModifyLayoutWhenRented() {
        warehouse.setStatus(WarehouseStatus.RENTED);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)).thenReturn(Optional.of(defaultLayout));
        when(rackRepository.findAllByLayoutId(defaultLayout.getId())).thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(defaultLayout.getId())).thenReturn(Collections.emptyList());
        when(layoutRepository.save(any(WarehouseLayout.class))).thenReturn(defaultLayout);

        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .racks(Collections.emptyList())
                .build();

        assertDoesNotThrow(() ->
                layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request));
    }

    @Test
    void testSaveContractLayoutUsesTheExistingTenantLayoutAndSharedGeometryValidation() {
        WarehouseLayout tenantLayout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .tenant(owner)
                .isDefault(false)
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .build();
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(Optional.of(tenantLayout));
        when(rackRepository.findAllByLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());
        when(binRepository.findAllByRackLayoutId(tenantLayout.getId())).thenReturn(Collections.emptyList());

        RackSaveRequest rack = RackSaveRequest.builder()
                .name("Contract Rack")
                .code("CONTRACT_RACK")
                .coordinateX(new BigDecimal("60"))
                .coordinateY(new BigDecimal("0"))
                .width(new BigDecimal("50"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();
        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("100"))
                .length(new BigDecimal("100"))
                .height(new BigDecimal("10"))
                .racks(List.of(rack))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> layoutService.saveContractLayout(warehouseId, userId, request));

        assertTrue(ex.getMessage().contains("parent bounds"));
        verify(layoutRepository, never()).save(any(WarehouseLayout.class));
    }

    @Test
    void testStableLayoutSnapshotSortsRacksBinsAndPositions() {
        WarehouseBinResponse binB = WarehouseBinResponse.builder()
                .id(UUID.randomUUID()).code("BIN-B").occupiedPositions(List.of("2:0", "1:0")).build();
        WarehouseBinResponse binA = WarehouseBinResponse.builder()
                .id(UUID.randomUUID()).code("BIN-A").occupiedPositions(List.of("1:0", "0:0")).build();
        RackResponse rackB = RackResponse.builder().id(UUID.randomUUID()).code("RACK-B")
                .bins(List.of(binB)).build();
        RackResponse rackA = RackResponse.builder().id(UUID.randomUUID()).code("RACK-A")
                .bins(List.of(binA)).build();
        WarehouseLayoutResponse source = WarehouseLayoutResponse.builder()
                .warehouseId(warehouseId)
                .racks(List.of(rackB, rackA))
                .positions(List.of("2:0", "0:0", "1:0"))
                .build();

        WarehouseLayoutResponse stable = layoutService.stabilizeLayoutSnapshot(source);

        assertEquals(List.of("RACK-A", "RACK-B"),
                stable.getRacks().stream().map(RackResponse::getCode).toList());
        assertEquals(List.of("BIN-A"), stable.getRacks().get(0).getBins().stream()
                .map(WarehouseBinResponse::getCode).toList());
        assertEquals(List.of("0:0", "1:0", "2:0"), stable.getPositions());
    }
}

