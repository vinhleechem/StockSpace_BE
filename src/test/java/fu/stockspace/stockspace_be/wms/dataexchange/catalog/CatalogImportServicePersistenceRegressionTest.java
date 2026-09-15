package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogImportServicePersistenceRegressionTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JOB_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final DataExchangeProperties properties = new DataExchangeProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WmsImportJobRepository jobRepository;
    private WmsImportRowRepository rowRepository;
    private EntityManager entityManager;
    private UnitOfMeasureRepository uomRepository;
    private CatalogImportService service;

    @BeforeEach
    void setUp() {
        jobRepository = mock(WmsImportJobRepository.class);
        rowRepository = mock(WmsImportRowRepository.class);
        entityManager = mock(EntityManager.class);
        uomRepository = mock(UnitOfMeasureRepository.class);

        User tenant = User.builder().id(TENANT_ID).build();
        User actor = User.builder().id(ACTOR_ID).build();
        when(entityManager.getReference(User.class, TENANT_ID)).thenReturn(tenant);
        when(entityManager.getReference(User.class, ACTOR_ID)).thenReturn(actor);
        when(uomRepository.findActiveVisibleByCode(eq(TENANT_ID), eq("KG")))
                .thenReturn(Optional.of(UnitOfMeasure.builder()
                        .id(UUID.randomUUID()).code("KG").name("Kilogram").build()));

        AtomicReference<WmsImportJob> persisted = new AtomicReference<>();
        when(jobRepository.saveAndFlush(any(WmsImportJob.class))).thenAnswer(invocation -> {
            WmsImportJob source = invocation.getArgument(0);
            WmsImportJob managed = managedCopy(source, tenant, actor);
            persisted.set(managed);
            return managed;
        });
        when(jobRepository.findReadableByTenantAndActor(JOB_ID, TENANT_ID, ACTOR_ID))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));

        WmsImportJobService jobService = new WmsImportJobService(
                jobRepository,
                rowRepository,
                new CanonicalContentHashService(objectMapper),
                properties,
                objectMapper,
                entityManager,
                mock(PlatformTransactionManager.class));

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
                rowRepository);
    }

    @Test
    void catalogValidatePersistsRowsAgainstTheManagedJobBeforeReadingTheResponse() {
        var response = service.validate(TENANT_ID, ACTOR_ID,
                new MockMultipartFile("file", "stockspace-catalog.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        catalogWorkbook()));

        assertEquals(WmsImportType.SKU_CATALOG, response.importType());
        assertEquals(JOB_ID, response.jobId());
        ArgumentCaptor<List<WmsImportRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(rowsCaptor.capture());
        assertEquals(1, rowsCaptor.getValue().size());
        assertEquals(response.jobId(), rowsCaptor.getValue().get(0).getJob().getId());
    }

    private byte[] catalogWorkbook() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
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
            writeRow(skus.createRow(1), "SKIP", "55555555-5555-5555-5555-555555555555",
                    "2026-09-14T00:00:00", "SYSTEM", "SKU-001", "Sample SKU", "", "", "KG", "1", "0.01", "");
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (Exception ex) {
            throw new AssertionError("Unable to create catalog workbook", ex);
        }
    }

    private void writeRow(Row row, String... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }

    private WmsImportJob managedCopy(WmsImportJob source, User tenant, User actor) {
        return WmsImportJob.builder()
                .id(JOB_ID)
                .tenant(tenant)
                .createdBy(actor)
                .importType(source.getImportType())
                .status(source.getStatus())
                .schemaVersion(source.getSchemaVersion())
                .originalFilename(source.getOriginalFilename())
                .fileSha256(source.getFileSha256())
                .contentSha256(source.getContentSha256())
                .contextMetadata(source.getContextMetadata())
                .totalRows(source.getTotalRows())
                .validRows(source.getValidRows())
                .invalidRows(source.getInvalidRows())
                .version(0L)
                .createdAt(LocalDateTime.now())
                .build();
    }

}
