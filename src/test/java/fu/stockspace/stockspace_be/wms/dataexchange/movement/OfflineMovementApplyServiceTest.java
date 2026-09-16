package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.inventory.StockFingerprintService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobStatus;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.dto.CreateInventoryReceiptRequest;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineMovementApplyServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = TENANT_ID; // Must be TENANT for apply
    private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WAREHOUSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LAYOUT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RACK_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID BIN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID SKU_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID RECEIPT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private WmsImportJobService jobService;
    private WmsImportRowRepository rowRepository;
    private InventoryReceiptService receiptService;
    private StockFingerprintService fingerprintService;
    private WarehouseRepository warehouseRepository;
    private WarehouseLayoutRepository layoutRepository;
    private WarehouseRackRepository rackRepository;
    private WarehouseBinRepository binRepository;
    private ProductSkuRepository skuRepository;
    private UserRepository userRepository;
    private TenantWarehouseAccessService accessService;
    private NotificationService notificationService;

    private OfflineMovementApplyService service;

    private User actor;
    private Warehouse warehouse;
    private WarehouseLayout defaultLayout;
    private WarehouseRack rack;
    private WarehouseBin bin;
    private ProductSku sku;
    private WmsImportJob job;

    @BeforeEach
    void setUp() {
        jobService = mock(WmsImportJobService.class);
        rowRepository = mock(WmsImportRowRepository.class);
        receiptService = mock(InventoryReceiptService.class);
        fingerprintService = mock(StockFingerprintService.class);
        warehouseRepository = mock(WarehouseRepository.class);
        layoutRepository = mock(WarehouseLayoutRepository.class);
        rackRepository = mock(WarehouseRackRepository.class);
        binRepository = mock(WarehouseBinRepository.class);
        skuRepository = mock(ProductSkuRepository.class);
        userRepository = mock(UserRepository.class);
        accessService = mock(TenantWarehouseAccessService.class);
        notificationService = mock(NotificationService.class);

        service = new OfflineMovementApplyService(
                jobService, rowRepository, receiptService, fingerprintService,
                warehouseRepository, layoutRepository, rackRepository, binRepository,
                skuRepository, userRepository, accessService, notificationService
        );

        Permission p1 = Permission.builder().name("INBOUND_CREATE").build();
        Permission p2 = Permission.builder().name("OUTBOUND_CREATE").build();
        Permission p3 = Permission.builder().name("INVENTORY_UPDATE").build();
        Role role = Role.builder().name(RoleType.ROLE_TENANT.name()).permissions(Set.of(p1, p2, p3)).build();
        actor = User.builder().id(ACTOR_ID).roles(Set.of(role)).build();
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));

        warehouse = Warehouse.builder().id(WAREHOUSE_ID).name("Test Warehouse").isActive(true).isDeleted(false).build();
        when(warehouseRepository.findByIdForUpdate(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));

        defaultLayout = WarehouseLayout.builder().id(LAYOUT_ID).warehouse(warehouse).tenant(null).isDefault(true).isActive(true).isDeleted(false).build();
        when(layoutRepository.findByWarehouseIdAndTenantId(WAREHOUSE_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(WAREHOUSE_ID)).thenReturn(Optional.of(defaultLayout));

        rack = WarehouseRack.builder().id(RACK_ID).layout(defaultLayout).code("RACK-01").isActive(true).isDeleted(false).build();
        when(rackRepository.findByIdAndIsDeletedFalse(RACK_ID)).thenReturn(Optional.of(rack));

        bin = WarehouseBin.builder().id(BIN_ID).rack(rack).code("BIN-01").isActive(true).isDeleted(false).build();
        when(binRepository.findByIdAndIsDeletedFalse(BIN_ID)).thenReturn(Optional.of(bin));

        sku = ProductSku.builder().id(SKU_ID).skuCode("SKU-01").name("Test SKU").isActive(true).isDeleted(false).build();
        when(skuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(SKU_ID, TENANT_ID)).thenReturn(Optional.of(sku));

        String fingerprint = "a".repeat(64);
        when(fingerprintService.fingerprint(WAREHOUSE_ID)).thenReturn(fingerprint);

        job = WmsImportJob.builder()
                .id(JOB_ID)
                .importType(WmsImportType.OFFLINE_MOVEMENT)
                .status(WmsImportJobStatus.VALIDATED)
                .warehouse(warehouse)
                .contextMetadata(Map.of("stock_fingerprint", fingerprint))
                .build();
    }

    @Test
    void apply_withDefaultLayout_succeeds() {
        WmsImportRow row = new WmsImportRow();
        row.setJob(job);
        row.setSheetName("MOVEMENTS");
        row.setRowNumber(2);
        row.setGroupKey("MOV-001");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movement_ref", "MOV-001");
        payload.put("sequence_no", 1);
        payload.put("type", "INBOUND");
        payload.put("occurred_at", "2026-09-01T10:00:00");
        payload.put("sku_id", SKU_ID.toString());
        payload.put("sku_code", "SKU-01");
        payload.put("quantity", 10);
        payload.put("layout_id", LAYOUT_ID.toString());
        payload.put("rack_id", RACK_ID.toString());
        payload.put("rack_code", "RACK-01");
        payload.put("bin_id", BIN_ID.toString());
        payload.put("bin_code", "BIN-01");

        row.setNormalizedPayload(payload);

        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID)).thenReturn(List.of(row));

        InventoryReceiptResponse pendingReceipt = InventoryReceiptResponse.builder()
                .id(RECEIPT_ID)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptResponse approvedReceipt = InventoryReceiptResponse.builder()
                .id(RECEIPT_ID)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.APPROVED)
                .build();

        when(receiptService.createReceiptForOfflineImport(eq(ACTOR_ID), any(CreateInventoryReceiptRequest.class), any(LocalDateTime.class)))
                .thenReturn(pendingReceipt);
        when(receiptService.approveReceiptForOfflineImport(ACTOR_ID, RECEIPT_ID))
                .thenReturn(approvedReceipt);

        WmsImportJobResponse mockJobResponse = new WmsImportJobResponse(
                JOB_ID, WmsImportType.OFFLINE_MOVEMENT, WmsImportJobStatus.APPLIED,
                "1.0", "test.xlsx", "hash1", "hash2", Map.of(), WAREHOUSE_ID, null,
                1, 1, 0, null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), List.of()
        );
        when(jobService.getJob(TENANT_ID, ACTOR_ID, JOB_ID)).thenReturn(mockJobResponse);

        when(jobService.applyJob(eq(TENANT_ID), eq(ACTOR_ID), eq(JOB_ID), any()))
                .thenAnswer(invocation -> {
                    Function<WmsImportJob, List<OfflineMovementApplyResponse.OfflineMovementReceiptResult>> callback =
                            invocation.getArgument(3);
                    return callback.apply(job);
                });

        OfflineMovementApplyResponse response = service.apply(TENANT_ID, ACTOR_ID, JOB_ID);

        assertNotNull(response);
        assertEquals(1, response.receipts().size());
        assertEquals("MOV-001", response.receipts().get(0).movementRef());
        assertEquals(RECEIPT_ID, response.receipts().get(0).receiptId());
    }
}
