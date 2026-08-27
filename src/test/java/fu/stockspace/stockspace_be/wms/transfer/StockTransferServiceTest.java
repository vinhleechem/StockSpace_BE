package fu.stockspace.stockspace_be.wms.transfer;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferSourceAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductSkuRepository productSkuRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;

    @InjectMocks
    private StockTransferService transferService;

    private UUID tenantId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID rackId;
    private UUID binId;
    private UUID skuId;
    private UUID batchId;
    private User tenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private WarehouseRack sourceRack;
    private WarehouseBin sourceBin;
    private ProductSku sku;
    private StockBatch sourceBatch;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        rackId = UUID.randomUUID();
        binId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        Role tenantRole = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        tenant = User.builder()
                .id(tenantId)
                .fullName("Tenant User")
                .roles(Set.of(tenantRole))
                .build();
        sourceWarehouse = Warehouse.builder().id(sourceWarehouseId).name("Source Warehouse").build();
        destinationWarehouse = Warehouse.builder().id(destinationWarehouseId).name("Destination Warehouse").build();
        sourceRack = WarehouseRack.builder().id(rackId).name("Rack A").build();
        sourceBin = WarehouseBin.builder().id(binId).rack(sourceRack).name("Bin A").build();
        sku = ProductSku.builder()
                .id(skuId)
                .tenant(tenant)
                .skuCode("SKU-001")
                .name("Product 1")
                .build();
        sourceBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(sourceWarehouse)
                .rack(sourceRack)
                .bin(sourceBin)
                .quantity(20)
                .build();
    }

    @Test
    void createTransfer_createsPendingWithoutChangingStock() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.createTransfer(tenantId,
                request(10, 10));

        assertEquals(StockTransferStatus.PENDING, response.getStatus());
        assertEquals(sourceWarehouseId, response.getSourceWarehouse().getId());
        assertEquals(destinationWarehouseId, response.getDestinationWarehouse().getId());
        assertEquals(1, response.getItems().size());
        assertEquals(10, response.getItems().get(0).getRequestedQuantity());
        assertEquals(10, response.getItems().get(0).getSourceAllocations().get(0).getQuantity());
        assertTrue(response.getItems().get(0).getDestinationAllocations().isEmpty());
        assertEquals(20, sourceBatch.getQuantity());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void createTransfer_rejectsAllocationTotalThatDoesNotMatchItemQuantity() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));

        assertThrows(BadRequestException.class,
                () -> transferService.createTransfer(tenantId, request(10, 9)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_rejectsBatchFromAnotherWarehouse() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        sourceBatch.setWarehouse(destinationWarehouse);
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));

        assertThrows(BadRequestException.class,
                () -> transferService.createTransfer(tenantId, request(10, 10)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_rejectsSystemOrAnotherTenantSku() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        ProductSku systemSku = ProductSku.builder().id(skuId).skuCode("SYSTEM-001").name("System SKU").build();
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(systemSku));

        assertThrows(ResourceNotFoundException.class,
                () -> transferService.createTransfer(tenantId, request(10, 10)));
        verify(stockBatchRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void getTransfers_isTenantScopedAndSupportsStatusFilter() {
        StockTransfer transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .createdBy(tenant)
                .status(StockTransferStatus.PENDING)
                .build();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.search(tenantId, sourceWarehouseId, destinationWarehouseId,
                StockTransferStatus.PENDING, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(transfer)));

        var response = transferService.getTransfers(tenantId, sourceWarehouseId,
                destinationWarehouseId, StockTransferStatus.PENDING, PageRequest.of(0, 10));

        assertEquals(1, response.getTotalElements());
        assertEquals(StockTransferStatus.PENDING, response.getContent().get(0).getStatus());
        verify(transferRepository).search(tenantId, sourceWarehouseId, destinationWarehouseId,
                StockTransferStatus.PENDING, null, PageRequest.of(0, 10));
    }

    @Test
    void getTransfer_returnsNotFoundForAnotherTenant() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByIdAndTenantIdAndIsDeletedFalse(transferId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> transferService.getTransfer(tenantId, transferId));
    }

    private void stubCreateAccess() {
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
    }

    private CreateStockTransferRequest request(int requestedQuantity, int sourceQuantity) {
        return CreateStockTransferRequest.builder()
                .sourceWarehouseId(sourceWarehouseId)
                .destinationWarehouseId(destinationWarehouseId)
                .items(List.of(StockTransferItemRequest.builder()
                        .skuId(skuId)
                        .requestedQuantity(requestedQuantity)
                        .sourceAllocations(List.of(StockTransferSourceAllocationRequest.builder()
                                .sourceStockBatchId(batchId)
                                .sourceRackId(rackId)
                                .sourceBinId(binId)
                                .quantity(sourceQuantity)
                                .build()))
                        .build()))
                .build();
    }
}
