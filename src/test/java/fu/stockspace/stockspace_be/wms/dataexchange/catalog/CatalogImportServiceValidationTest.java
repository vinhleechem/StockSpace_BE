package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobStatus;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogImportServiceValidationTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JOB_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final DataExchangeProperties properties = new DataExchangeProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WmsImportJobService jobService;
    private UnitOfMeasureRepository uomRepository;
    private CatalogImportService service;

    @BeforeEach
    void setUp() {
        jobService = mock(WmsImportJobService.class);
        uomRepository = mock(UnitOfMeasureRepository.class);

        service = new CatalogImportService(
                new XlsxWorkbookReader(properties),
                new CanonicalContentHashService(objectMapper),
                jobService,
                properties,
                objectMapper,
                mock(fu.stockspace.stockspace_be.auth.repository.UserRepository.class),
                mock(ProductCategoryRepository.class),
                mock(ProductSkuRepository.class),
                uomRepository,
                mock(ProductCategoryService.class),
                mock(ProductSkuService.class),
                mock(fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository.class));
    }

    @Test
    void validatesWorkbookWithTheCatalogExportContract() throws Exception {
        UnitOfMeasure uom = UnitOfMeasure.builder()
                .id(UUID.fromString("44444444-4444-4444-4444-444444444444"))
                .code("KG")
                .name("Kilogram")
                .build();
        when(uomRepository.findAllActiveByTenantOrSystem(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(uom)));

        WmsImportJob job = WmsImportJob.builder()
                .id(JOB_ID)
                .importType(WmsImportType.SKU_CATALOG)
                .status(WmsImportJobStatus.VALIDATED)
                .schemaVersion("1.0")
                .originalFilename("stockspace-catalog-2026-09-14.xlsx")
                .fileSha256("a".repeat(64))
                .contentSha256("b".repeat(64))
                .build();
        when(jobService.createJob(any())).thenReturn(job);
        when(jobService.getJob(TENANT_ID, ACTOR_ID, JOB_ID)).thenReturn(
                new WmsImportJobResponse(JOB_ID, WmsImportType.SKU_CATALOG, WmsImportJobStatus.VALIDATED,
                        "1.0", job.getOriginalFilename(), job.getFileSha256(), job.getContentSha256(),
                        Map.of(), null, null, 1, 1, 0, null, LocalDateTime.now(), LocalDateTime.now(), null,
                        List.of()));

        byte[] workbook = catalogWorkbook();
        WmsImportJobResponse response = service.validate(TENANT_ID, ACTOR_ID,
                new MockMultipartFile("file", "stockspace-catalog-2026-09-14.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));

        assertNotNull(response);
        assertEquals(WmsImportJobStatus.VALIDATED, response.status());
        ArgumentCaptor<fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobCommand> command =
                ArgumentCaptor.forClass(fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobCommand.class);
        verify(jobService).createJob(command.capture());
        assertEquals(TENANT_ID, command.getValue().tenantId());
        assertEquals(ACTOR_ID, command.getValue().createdById());
        assertEquals(WmsImportType.SKU_CATALOG, command.getValue().importType());
        assertEquals(1, command.getValue().rows().size());
        assertTrue(command.getValue().rows().get(0).validationErrors().isEmpty());
    }

    @Test
    void rejectsNonXlsxInputBeforeCreatingAnImportJob() {
        assertThrows(XlsxFileException.class, () -> service.validate(TENANT_ID, ACTOR_ID,
                new MockMultipartFile("file", "catalog.xls", "application/vnd.ms-excel",
                        new byte[]{1, 2, 3})));

        org.mockito.Mockito.verify(jobService, never()).createJob(any());
    }

    @Test
    void returnsInvalidJobForRowsThatFailBusinessValidation() {
        WmsImportJob job = WmsImportJob.builder()
                .id(JOB_ID)
                .importType(WmsImportType.SKU_CATALOG)
                .status(WmsImportJobStatus.INVALID)
                .schemaVersion("1.0")
                .originalFilename("catalog.xlsx")
                .fileSha256("a".repeat(64))
                .contentSha256("b".repeat(64))
                .build();
        when(jobService.createJob(any())).thenReturn(job);
        when(jobService.getJob(TENANT_ID, ACTOR_ID, JOB_ID)).thenReturn(
                new WmsImportJobResponse(JOB_ID, WmsImportType.SKU_CATALOG, WmsImportJobStatus.INVALID,
                        "1.0", "catalog.xlsx", job.getFileSha256(), job.getContentSha256(), Map.of(), null, null,
                        1, 0, 1, null, LocalDateTime.now(), LocalDateTime.now(), null, List.of()));

        WmsImportJobResponse response = service.validate(TENANT_ID, ACTOR_ID,
                new MockMultipartFile("file", "catalog.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        catalogWorkbookWithInvalidRow()));

        assertEquals(WmsImportJobStatus.INVALID, response.status());
        verify(jobService).createJob(any());
    }

    @Test
    void rejectsWorkbookWithUnexpectedHeadersBeforeCreatingAnImportJob() {
        assertThrows(fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException.class,
                () -> service.validate(TENANT_ID, ACTOR_ID,
                        new MockMultipartFile("file", "catalog.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                catalogWorkbookWithUnexpectedHeader())));

        verify(jobService, never()).createJob(any());
    }

    private byte[] catalogWorkbook() {
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            XlsxWorkbookWriter.addMetadataSheet(workbook, Map.of(
                    "schema_version", "1.0",
                    "workbook_type", "SKU_CATALOG"));

            Sheet categories = workbook.createSheet("CATEGORIES");
            writeRow(categories.createRow(0), "action", "category_key", "category_id", "source",
                    "name", "default_attributes_json");

            Sheet skus = workbook.createSheet("SKUS");
            writeRow(skus.createRow(0), "action", "sku_id", "source_updated_at", "source", "sku_code",
                    "name", "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3",
                    "specifications_json");
            writeRow(skus.createRow(1), "SKIP", "55555555-5555-5555-5555-555555555555", "2026-09-14T00:00:00",
                    "SYSTEM", "SKU-001", "Sample SKU", "", "", "KG", "1", "0.01", "");
            return XlsxWorkbookWriter.toBytes(workbook);
    }

    private byte[] catalogWorkbookWithInvalidRow() {
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        XlsxWorkbookWriter.addMetadataSheet(workbook, Map.of(
                "schema_version", "1.0",
                "workbook_type", "SKU_CATALOG"));
        Sheet categories = workbook.createSheet("CATEGORIES");
        writeRow(categories.createRow(0), "action", "category_key", "category_id", "source",
                "name", "default_attributes_json");
        Sheet skus = workbook.createSheet("SKUS");
        writeRow(skus.createRow(0), "action", "sku_id", "source_updated_at", "source", "sku_code",
                "name", "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3",
                "specifications_json");
        writeRow(skus.createRow(1), "NOT_SUPPORTED", "", "", "TENANT", "SKU-001", "Sample SKU",
                "", "", "", "", "", "");
        return XlsxWorkbookWriter.toBytes(workbook);
    }

    private byte[] catalogWorkbookWithUnexpectedHeader() {
        Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        XlsxWorkbookWriter.addMetadataSheet(workbook, Map.of(
                "schema_version", "1.0",
                "workbook_type", "SKU_CATALOG"));
        Sheet categories = workbook.createSheet("CATEGORIES");
        writeRow(categories.createRow(0), "action", "category_key", "category_id", "source",
                "name", "unexpected_column");
        Sheet skus = workbook.createSheet("SKUS");
        writeRow(skus.createRow(0), "action", "sku_id", "source_updated_at", "source", "sku_code",
                "name", "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3",
                "specifications_json");
        return XlsxWorkbookWriter.toBytes(workbook);
    }

    private void writeRow(Row row, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
