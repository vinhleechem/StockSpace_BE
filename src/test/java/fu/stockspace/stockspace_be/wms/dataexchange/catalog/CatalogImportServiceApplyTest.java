package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobStatus;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.product.dto.CreateCategoryRequest;
import fu.stockspace.stockspace_be.wms.product.dto.CreateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.dto.ProductCategoryResponse;
import fu.stockspace.stockspace_be.wms.product.dto.UpdateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogImportServiceApplyTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JOB_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CATEGORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SKU_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID UOM_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private WmsImportJobService jobService;
    private WmsImportRowRepository rowRepository;
    private UnitOfMeasureRepository uomRepository;
    private ProductCategoryService categoryService;
    private ProductSkuService skuService;
    private CatalogImportService service;
    private WmsImportJob job;

    @BeforeEach
    void setUp() {
        DataExchangeProperties properties = new DataExchangeProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        jobService = mock(WmsImportJobService.class);
        rowRepository = mock(WmsImportRowRepository.class);
        uomRepository = mock(UnitOfMeasureRepository.class);
        categoryService = mock(ProductCategoryService.class);
        skuService = mock(ProductSkuService.class);

        service = new CatalogImportService(
                new XlsxWorkbookReader(properties),
                new CanonicalContentHashService(objectMapper),
                jobService,
                properties,
                objectMapper,
                mock(UserRepository.class),
                mock(ProductCategoryRepository.class),
                mock(ProductSkuRepository.class),
                uomRepository,
                categoryService,
                skuService,
                rowRepository);

        job = WmsImportJob.builder()
                .id(JOB_ID)
                .importType(WmsImportType.SKU_CATALOG)
                .status(WmsImportJobStatus.VALIDATED)
                .build();
        when(jobService.getValidatedJobForTenant(TENANT_ID, ACTOR_ID, JOB_ID)).thenReturn(job);
    }

    @Test
    void appliesCategoriesAndSkusFromTheirOwnSheets() {
        WmsImportRow category = row("CATEGORIES", 2, payload(
                "action", "CREATE",
                "category_key", "electronics",
                "name", "Electronics"));
        WmsImportRow sku = row("SKUS", 2, payload(
                "action", "CREATE",
                "category_key", "electronics",
                "sku_code", "SKU-001",
                "name", "USB cable",
                "uom_code", "THUNG",
                "unit_weight_kg", "1.5",
                "unit_volume_m3", "0.01"));
        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID))
                .thenReturn(List.of(category, sku));
        when(uomRepository.findActiveVisibleByCode(TENANT_ID, "THUNG"))
                .thenReturn(Optional.of(uom()));
        when(categoryService.createCategory(eq(TENANT_ID), any(CreateCategoryRequest.class)))
                .thenReturn(ProductCategoryResponse.builder().id(CATEGORY_ID).build());
        stubDomainApply();

        service.apply(TENANT_ID, ACTOR_ID, JOB_ID);

        verify(categoryService).createCategory(eq(TENANT_ID), any(CreateCategoryRequest.class));
        verify(skuService).createSku(eq(TENANT_ID), any(CreateSkuRequest.class));
        verify(uomRepository).findActiveVisibleByCode(TENANT_ID, "THUNG");
    }

    @Test
    void preflightsAllSkuUomsBeforeWritingCategoriesOrSkus() {
        WmsImportRow category = row("CATEGORIES", 2, payload(
                "action", "CREATE",
                "category_key", "electronics",
                "name", "Electronics"));
        WmsImportRow sku = row("SKUS", 2, payload(
                "action", "CREATE",
                "category_key", "electronics",
                "sku_code", "SKU-001",
                "name", "USB cable",
                "uom_code", "MISSING",
                "unit_weight_kg", "1.5",
                "unit_volume_m3", "0.01"));
        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID))
                .thenReturn(List.of(category, sku));
        when(uomRepository.findActiveVisibleByCode(TENANT_ID, "MISSING"))
                .thenReturn(Optional.empty());
        stubDomainApply();

        assertThrows(ResourceNotFoundException.class,
                () -> service.apply(TENANT_ID, ACTOR_ID, JOB_ID));

        verifyNoInteractions(categoryService, skuService);
    }

    @Test
    void skipsSkuRowsWithoutResolvingTheirUom() {
        WmsImportRow category = row("CATEGORIES", 2, payload(
                "action", "SKIP",
                "category_key", "electronics"));
        WmsImportRow sku = row("SKUS", 2, payload(
                "action", "SKIP",
                "sku_id", SKU_ID.toString(),
                "sku_code", "SKU-001"));
        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID))
                .thenReturn(List.of(category, sku));
        stubDomainApply();

        service.apply(TENANT_ID, ACTOR_ID, JOB_ID);

        verifyNoInteractions(uomRepository, categoryService, skuService);
    }

    @Test
    void appliesSkuUpdateWithThePreflightedUom() {
        WmsImportRow sku = row("SKUS", 3, payload(
                "action", "UPDATE",
                "sku_id", SKU_ID.toString(),
                "sku_code", "SKU-001",
                "name", "Updated USB cable",
                "uom_code", "THUNG",
                "unit_weight_kg", "2.0",
                "unit_volume_m3", "0.02"));
        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID))
                .thenReturn(List.of(sku));
        when(uomRepository.findActiveVisibleByCode(TENANT_ID, "THUNG"))
                .thenReturn(Optional.of(uom()));
        stubDomainApply();

        service.apply(TENANT_ID, ACTOR_ID, JOB_ID);

        verify(skuService).updateSku(eq(TENANT_ID), eq(SKU_ID), any(UpdateSkuRequest.class));
        verify(skuService, never()).createSku(eq(TENANT_ID), any(CreateSkuRequest.class));
    }

    @Test
    void skipsRowsWithValidationErrorsBeforeAnyDomainWrite() {
        WmsImportRow invalidSku = row("SKUS", 4, payload(
                "action", "CREATE",
                "sku_code", "SKU-001",
                "uom_code", "MISSING"));
        invalidSku.setValidationErrors(new ArrayList<>(List.of(
                Map.of("code", "UOM_NOT_VISIBLE", "message", "uom_code is not visible"))));
        when(rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(JOB_ID))
                .thenReturn(List.of(invalidSku));
        stubDomainApply();

        service.apply(TENANT_ID, ACTOR_ID, JOB_ID);

        verifyNoInteractions(uomRepository, categoryService, skuService);
    }

    private void stubDomainApply() {
        when(jobService.applyJob(eq(TENANT_ID), eq(ACTOR_ID), eq(JOB_ID), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<WmsImportJob, WmsImportJobResponse> callback = invocation.getArgument(3);
                    return callback.apply(job);
                });
    }

    private WmsImportRow row(String sheetName, int rowNumber, Map<String, Object> payload) {
        return WmsImportRow.builder()
                .id(UUID.randomUUID())
                .sheetName(sheetName)
                .rowNumber(rowNumber)
                .normalizedPayload(new LinkedHashMap<>(payload))
                .validationErrors(new ArrayList<>())
                .build();
    }

    private UnitOfMeasure uom() {
        return UnitOfMeasure.builder()
                .id(UOM_ID)
                .code("THUNG")
                .name("Box")
                .build();
    }

    private Map<String, Object> payload(String... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            payload.put(values[index], values[index + 1]);
        }
        return payload;
    }
}
