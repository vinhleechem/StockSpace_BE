package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;
import fu.stockspace.stockspace_be.wms.capacity.dto.BinCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.RackCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.WarehouseLayoutCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.service.WarehouseCapacityService;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.ReceiptItemResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditItemResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditService;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.WarehouseSummaryResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentTenantWmsChatToolsTest {

    @Mock
    private InventoryReceiptService receiptService;
    @Mock
    private InventoryAuditService auditService;
    @Mock
    private StockTransferService transferService;
    @Mock
    private WarehouseCapacityService capacityService;
    @Mock
    private WarehouseService warehouseService;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID warehouseId;
    private ChatRequestContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        context = new ChatRequestContext(userId, warehouseId, "Kho hiện tại");
    }

    @Test
    void receiptToolUsesCurrentWarehouseAndLocalizesStatus() throws Exception {
        InventoryReceiptResponse receipt = InventoryReceiptResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .warehouseName("Kho hiện tại")
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .items(List.of(ReceiptItemResponse.builder()
                        .skuCode("SKU-01").skuName("Sản phẩm A").quantity(12).build()))
                .createdAt(LocalDateTime.of(2026, 9, 4, 9, 0))
                .build();
        when(receiptService.getReceiptsByWarehouse(
                eq(userId), eq(warehouseId), eq(DocumentType.OUTBOUND), any(Pageable.class)))
                .thenReturn(page(List.of(receipt), 1));

        JsonNode result = objectMapper.readTree(new GetInventoryReceiptsTool(objectMapper, receiptService)
                .executeWithContext(Map.of("type", "OUTBOUND"), context));

        assertEquals("Phiếu xuất", result.at("/receipts/0/type").asText());
        assertEquals("Chờ duyệt", result.at("/receipts/0/status").asText());
        assertEquals(12, result.at("/receipts/0/totalQuantity").asInt());
        assertFalse(result.toString().contains("PENDING"));
    }

    @Test
    void auditToolSummarizesDiscrepanciesForCurrentWarehouse() throws Exception {
        InventoryAuditResponse audit = InventoryAuditResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .warehouseName("Kho hiện tại")
                .status(AuditStatus.SUBMITTED)
                .items(List.of(
                        InventoryAuditItemResponse.builder().discrepancy(-2).build(),
                        InventoryAuditItemResponse.builder().discrepancy(5).build(),
                        InventoryAuditItemResponse.builder().discrepancy(0).build()))
                .build();
        when(auditService.getMyAudits(eq(userId), eq(warehouseId), any(Pageable.class)))
                .thenReturn(page(List.of(audit), 1));

        JsonNode result = objectMapper.readTree(new GetInventoryAuditsTool(objectMapper, auditService)
                .executeWithContext(Map.of(), context));

        assertEquals("Đã gửi kết quả", result.at("/audits/0/status").asText());
        assertEquals(2, result.at("/audits/0/itemsWithDiscrepancy").asInt());
        assertEquals(3, result.at("/audits/0/netDiscrepancy").asInt());
        assertFalse(result.toString().contains("SUBMITTED"));
    }

    @Test
    void transferToolCombinesDeparturesAndArrivalsForCurrentWarehouse() throws Exception {
        UUID destinationId = UUID.randomUUID();
        StockTransferResponse transfer = StockTransferResponse.builder()
                .id(UUID.randomUUID())
                .status(StockTransferStatus.IN_TRANSIT)
                .sourceWarehouse(WarehouseSummaryResponse.builder().id(warehouseId).name("Kho hiện tại").build())
                .destinationWarehouse(WarehouseSummaryResponse.builder().id(destinationId).name("Kho đích").build())
                .items(List.of(StockTransferItemResponse.builder().requestedQuantity(20).build()))
                .createdAt(LocalDateTime.of(2026, 9, 4, 10, 0))
                .build();
        when(transferService.getTransfers(
                eq(userId), eq(warehouseId), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page(List.of(transfer), 1));
        when(transferService.getTransfers(
                eq(userId), eq(null), eq(warehouseId), eq(null), any(Pageable.class)))
                .thenReturn(page(List.of(), 0));

        JsonNode result = objectMapper.readTree(new GetStockTransfersTool(objectMapper, transferService)
                .executeWithContext(Map.of(), context));

        assertEquals("Đang vận chuyển", result.at("/transfers/0/status").asText());
        assertEquals("Kho đích", result.at("/transfers/0/destinationWarehouse").asText());
        assertEquals(20, result.at("/transfers/0/totalQuantity").asInt());
        assertFalse(result.toString().contains("IN_TRANSIT"));
    }

    @Test
    void capacityToolReturnsPhysicalUtilizationWithoutInternalIds() throws Exception {
        RackCapacityResponse rack = RackCapacityResponse.builder()
                .rackId(UUID.randomUUID())
                .rackName("Kệ A")
                .currentWeightKg(new BigDecimal("800"))
                .maxWeightKg(new BigDecimal("1000"))
                .weightUtilizationPercent(new BigDecimal("80"))
                .capacityStatus(CapacityStatus.AVAILABLE)
                .bins(List.of(BinCapacityResponse.builder().capacityStatus(CapacityStatus.FULL).build()))
                .build();
        when(capacityService.getCapacity(userId, warehouseId, null))
                .thenReturn(WarehouseLayoutCapacityResponse.builder()
                        .warehouseId(warehouseId)
                        .warehouseName("Kho hiện tại")
                        .racks(List.of(rack))
                        .build());

        JsonNode result = objectMapper.readTree(new GetWarehouseCapacityTool(objectMapper, capacityService)
                .executeWithContext(Map.of(), context));

        assertEquals("Kệ A", result.at("/racks/0/rack").asText());
        assertEquals("Còn sức chứa", result.at("/racks/0/status").asText());
        assertEquals(1, result.at("/racks/0/fullOrOverCapacityBins").asInt());
        assertFalse(result.toString().contains("rackId"));
        assertFalse(result.toString().contains(warehouseId.toString()));
    }

    @Test
    void ownerContactRequiresLoginAndUsesPublishedWarehouseService() throws Exception {
        GetWarehouseOwnerContactTool tool = new GetWarehouseOwnerContactTool(objectMapper, warehouseService);
        JsonNode guestResult = objectMapper.readTree(
                tool.execute(Map.of("warehouseId", warehouseId.toString()), null));
        assertTrue(guestResult.has("error"));
        verifyNoInteractions(warehouseService);

        when(warehouseService.getOwnerContact(warehouseId)).thenReturn(WarehouseOwnerContactResponse.builder()
                .warehouseId(warehouseId)
                .ownerId(UUID.randomUUID())
                .ownerName("Nguyễn Văn A")
                .phone("0901234567")
                .build());
        JsonNode tenantResult = objectMapper.readTree(
                tool.execute(Map.of("warehouseId", warehouseId.toString()), userId));

        assertEquals("Nguyễn Văn A", tenantResult.get("contactName").asText());
        assertEquals("0901234567", tenantResult.get("phone").asText());
        assertFalse(tenantResult.has("ownerId"));
        assertFalse(tenantResult.has("warehouseId"));
        verify(warehouseService).getOwnerContact(warehouseId);
    }

    private <T> PagedResponse<T> page(List<T> content, long total) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(0)
                .size(10)
                .totalElements(total)
                .totalPages(total == 0 ? 0 : 1)
                .last(true)
                .build();
    }
}
