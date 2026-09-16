package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.inventory.StockFingerprintService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobCommand;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobStatus;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineMovementWorkbookServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WAREHOUSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LAYOUT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RACK_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID BIN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID SKU_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID JOB_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private UserRepository userRepository;
    private WarehouseRepository warehouseRepository;
    private WarehouseLayoutRepository layoutRepository;
    private WarehouseRackRepository rackRepository;
    private WarehouseBinRepository binRepository;
    private ProductSkuRepository skuRepository;
    private TenantWarehouseAccessService accessService;
    private StockFingerprintService fingerprintService;
    private WmsImportJobService jobService;

    private OfflineMovementWorkbookService service;

    private User actor;
    private Warehouse warehouse;
    private WarehouseLayout defaultLayout;
    private WarehouseRack rack;
    private WarehouseBin bin;
    private ProductSku sku;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        layoutRepository = mock(WarehouseLayoutRepository.class);
        rackRepository = mock(WarehouseRackRepository.class);
        binRepository = mock(WarehouseBinRepository.class);
        skuRepository = mock(ProductSkuRepository.class);
        accessService = mock(TenantWarehouseAccessService.class);
        fingerprintService = mock(StockFingerprintService.class);
        jobService = mock(WmsImportJobService.class);

        DataExchangeProperties properties = new DataExchangeProperties();
        XlsxWorkbookReader workbookReader = new XlsxWorkbookReader(properties);
        CanonicalContentHashService hashService = new CanonicalContentHashService(new com.fasterxml.jackson.databind.ObjectMapper());

        service = new OfflineMovementWorkbookService(
                workbookReader, hashService, jobService, properties, userRepository,
                warehouseRepository, layoutRepository, rackRepository, binRepository,
                skuRepository, accessService, fingerprintService
        );

        Permission p1 = Permission.builder().name("INBOUND_CREATE").build();
        Permission p2 = Permission.builder().name("OUTBOUND_CREATE").build();
        Permission p3 = Permission.builder().name("INVENTORY_READ").build();
        Role role = Role.builder().name(RoleType.ROLE_TENANT.name()).permissions(Set.of(p1, p2, p3)).build();
        actor = User.builder().id(ACTOR_ID).roles(Set.of(role)).build();
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));

        warehouse = Warehouse.builder().id(WAREHOUSE_ID).name("Test Warehouse").isActive(true).isDeleted(false).build();
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));

        defaultLayout = WarehouseLayout.builder().id(LAYOUT_ID).warehouse(warehouse).tenant(null).isDefault(true).isActive(true).isDeleted(false).build();
        when(layoutRepository.findByWarehouseIdAndTenantId(WAREHOUSE_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(layoutRepository.findByWarehouseIdAndIsDefaultTrue(WAREHOUSE_ID)).thenReturn(Optional.of(defaultLayout));

        rack = WarehouseRack.builder().id(RACK_ID).layout(defaultLayout).code("RACK-01").isActive(true).isDeleted(false).build();
        when(rackRepository.findAllByLayoutId(LAYOUT_ID)).thenReturn(List.of(rack));

        bin = WarehouseBin.builder().id(BIN_ID).rack(rack).code("BIN-01").isActive(true).isDeleted(false).build();
        when(binRepository.findAllByRackId(RACK_ID)).thenReturn(List.of(bin));

        sku = ProductSku.builder().id(SKU_ID).skuCode("SKU-01").name("Test SKU").isActive(true).isDeleted(false).build();
        when(skuRepository.findAllActiveByTenantOrSystem(eq(TENANT_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(sku)));
        when(skuRepository.findVisibleActiveByTenantAndSkuCodes(eq(TENANT_ID), eq(Set.of("sku-01"))))
                .thenReturn(List.of(sku));

        when(fingerprintService.fingerprint(WAREHOUSE_ID)).thenReturn("a".repeat(64));
    }

    @Test
    void renderTemplate_withDefaultLayout_succeeds() {
        byte[] bytes = service.renderTemplate(TENANT_ID, ACTOR_ID, WAREHOUSE_ID);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void validate_withDefaultLayoutAndHistoricalOccurredAt_succeeds() throws IOException {
        byte[] workbookBytes = buildMovementWorkbook("2026-09-01 10:00:00");
        MockMultipartFile file = new MockMultipartFile("file", "movements.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        WmsImportJob mockJob = WmsImportJob.builder().id(JOB_ID).build();
        when(jobService.createJob(any())).thenReturn(mockJob);

        WmsImportJobResponse mockResponse = new WmsImportJobResponse(
                JOB_ID, WmsImportType.OFFLINE_MOVEMENT, WmsImportJobStatus.VALIDATED,
                "1.0", "movements.xlsx", "hash1", "hash2", Map.of(), WAREHOUSE_ID, null,
                1, 1, 0, null, LocalDateTime.now(), LocalDateTime.now(), null, List.of()
        );
        when(jobService.getJob(TENANT_ID, ACTOR_ID, JOB_ID)).thenReturn(mockResponse);

        WmsImportJobResponse response = service.validate(TENANT_ID, ACTOR_ID, WAREHOUSE_ID, file);

        assertNotNull(response);
        assertEquals(JOB_ID, response.jobId());

        ArgumentCaptor<WmsImportJobCommand> captor = ArgumentCaptor.forClass(WmsImportJobCommand.class);
        verify(jobService).createJob(captor.capture());

        WmsImportJobCommand command = captor.getValue();
        assertEquals(1, command.rows().size());
        assertTrue(command.rows().get(0).validationErrors().isEmpty(),
                "Errors should be empty, but got: " + command.rows().get(0).validationErrors());
    }

    private byte[] buildMovementWorkbook(String occurredAt) throws IOException {
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            Map<String, Object> metadata = Map.of(
                    "schema_version", "1.0",
                    "workbook_type", "OFFLINE_MOVEMENT",
                    "export_id", UUID.randomUUID(),
                    "generated_at", ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString(),
                    "tenant_id", TENANT_ID.toString(),
                    "warehouse_id", WAREHOUSE_ID.toString(),
                    "warehouse_name", "Test Warehouse",
                    "stock_fingerprint", "a".repeat(64),
                    "timezone", "Asia/Ho_Chi_Minh"
            );
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);

            Sheet sheet = workbook.createSheet("MOVEMENTS");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "movement_ref", "sequence_no", "type", "occurred_at", "sender_name", "receiver_name",
                    "sku_code", "quantity", "rack_code", "bin_code", "note"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("MOV-001");
            row.createCell(1).setCellValue(1);
            row.createCell(2).setCellValue("INBOUND");
            row.createCell(3).setCellValue(occurredAt);
            row.createCell(4).setCellValue("Supplier");
            row.createCell(5).setCellValue("Warehouse Receiver");
            row.createCell(6).setCellValue("SKU-01");
            row.createCell(7).setCellValue(10);
            row.createCell(8).setCellValue("RACK-01");
            row.createCell(9).setCellValue("BIN-01");
            row.createCell(10).setCellValue("Note");

            return XlsxWorkbookWriter.toBytes(workbook);
        }
    }
}
