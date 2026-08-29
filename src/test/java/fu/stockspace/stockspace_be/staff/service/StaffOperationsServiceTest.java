package fu.stockspace.stockspace_be.staff.service;

import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.dto.StaffOperationResponse;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffOperationsServiceTest {

    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private InventoryReceiptRepository receiptRepository;
    @Mock
    private InventoryAuditRepository auditRepository;
    @Mock
    private StockTransferRepository transferRepository;

    @InjectMocks
    private StaffOperationsService operationsService;

    private UUID staffId;
    private UUID tenantId;
    private UUID warehouseAId;
    private UUID warehouseBId;
    private Warehouse warehouseA;
    private Warehouse warehouseB;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        warehouseAId = UUID.randomUUID();
        warehouseBId = UUID.randomUUID();
        warehouseA = Warehouse.builder().id(warehouseAId).name("Warehouse A").build();
        warehouseB = Warehouse.builder().id(warehouseBId).name("Warehouse B").build();
    }

    @Test
    void getOperationsReturnsActionableOperationsFromAssignedWarehouses() {
        when(accessService.findAccessibleContractWarehouses(tenantId, staffId))
                .thenReturn(List.of(warehouseA, warehouseB));
        InventoryReceipt pendingReceipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouseA)
                .createdAt(LocalDateTime.of(2026, 8, 27, 10, 0))
                .build();
        InventoryAudit pendingAudit = InventoryAudit.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouseA)
                .status(AuditStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 8, 27, 11, 0))
                .build();
        StockTransfer pendingTransfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .sourceWarehouse(warehouseA)
                .destinationWarehouse(warehouseB)
                .status(StockTransferStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 8, 27, 12, 0))
                .build();
        when(receiptRepository.findActiveOperationsByWarehouseIds(List.of(warehouseAId, warehouseBId)))
                .thenReturn(List.of(pendingReceipt));
        when(auditRepository.findActiveOperationsByWarehouseIds(List.of(warehouseAId, warehouseBId)))
                .thenReturn(List.of(pendingAudit));
        when(transferRepository.findActiveOperationsForStaff(tenantId, List.of(warehouseAId, warehouseBId)))
                .thenReturn(List.of(pendingTransfer));

        PagedResponse<StaffOperationResponse> response = operationsService.getOperations(
                staffId, tenantId, null, null, null, PageRequest.of(0, 20));

        assertEquals(3, response.getTotalElements());
        assertEquals("TRANSFER", response.getContent().get(0).getOperationType());
        assertEquals("AUDIT", response.getContent().get(1).getOperationType());
        assertEquals(List.of("VIEW", "SUBMIT"), response.getContent().get(1).getAllowedActions());
        assertEquals("RECEIPT", response.getContent().get(2).getOperationType());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isLast());
    }

    @Test
    void getOperationsRejectsWarehouseOutsideActiveAssignment() {
        when(accessService.findAccessibleContractWarehouses(tenantId, staffId))
                .thenReturn(List.of(warehouseA));

        assertThrows(ForbiddenException.class, () -> operationsService.getOperations(
                staffId, tenantId, warehouseBId, null, null, PageRequest.of(0, 20)));

        verify(receiptRepository, never()).findActiveOperationsByWarehouseIds(eq(List.of(warehouseBId)));
        verify(auditRepository, never()).findActiveOperationsByWarehouseIds(eq(List.of(warehouseBId)));
        verify(transferRepository, never()).findActiveOperationsForStaff(eq(tenantId), eq(List.of(warehouseBId)));
    }

    @Test
    void getOperationsSupportsTypeAndStatusFilters() {
        when(accessService.findAccessibleContractWarehouses(tenantId, staffId))
                .thenReturn(List.of(warehouseA));
        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouseA)
                .status(AuditStatus.APPROVED)
                .build();
        when(auditRepository.findActiveOperationsByWarehouseIds(List.of(warehouseAId)))
                .thenReturn(List.of(approvedAudit));

        PagedResponse<StaffOperationResponse> response = operationsService.getOperations(
                staffId, tenantId, warehouseAId, "audit", "approved", PageRequest.of(0, 20));

        assertEquals(1, response.getTotalElements());
        assertEquals("AUDIT", response.getContent().get(0).getOperationType());
        assertEquals("APPROVED", response.getContent().get(0).getStatus());
        verify(receiptRepository, never()).findActiveOperationsByWarehouseIds(eq(List.of(warehouseAId)));
        verify(transferRepository, never()).findActiveOperationsForStaff(eq(tenantId), eq(List.of(warehouseAId)));
    }

    @Test
    void getOperationsRejectsUnknownType() {
        when(accessService.findAccessibleContractWarehouses(tenantId, staffId))
                .thenReturn(List.of(warehouseA));

        assertThrows(BadRequestException.class, () -> operationsService.getOperations(
                staffId, tenantId, null, "TASK", null, PageRequest.of(0, 20)));
    }
}
