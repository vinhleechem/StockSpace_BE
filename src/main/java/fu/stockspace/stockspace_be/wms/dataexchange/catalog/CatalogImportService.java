package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobCommand;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowInput;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.product.dto.CreateCategoryRequest;
import fu.stockspace.stockspace_be.wms.product.dto.CreateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.dto.ProductCategoryResponse;
import fu.stockspace.stockspace_be.wms.product.dto.UpdateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogImportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Set<String> CATEGORIES_HEADERS = Set.of(
            "action", "category_key", "category_id", "source", "name", "default_attributes_json");
    private static final Set<String> SKUS_HEADERS = Set.of(
            "action", "sku_id", "source_updated_at", "source", "sku_code", "name",
            "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3",
            "specifications_json");

    private final XlsxWorkbookReader workbookReader;
    private final CanonicalContentHashService hashService;
    private final WmsImportJobService jobService;
    private final DataExchangeProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductSkuRepository skuRepository;
    private final UnitOfMeasureRepository uomRepository;
    private final ProductCategoryService categoryService;
    private final ProductSkuService skuService;

    @Transactional
    public WmsImportJobResponse validate(UUID tenantId, UUID actorId, MultipartFile file) {
        byte[] bytes = readBytes(file);
        ParsedCatalog parsed = parse(tenantId, bytes, file.getOriginalFilename());
        WmsImportJob job = jobService.createJob(new WmsImportJobCommand(
                tenantId, actorId, null, null, WmsImportType.SKU_CATALOG, SCHEMA_VERSION,
                file.getOriginalFilename(), hashService.sha256(bytes), Map.of(), parsed.rows()));
        return jobService.getJob(tenantId, actorId, job.getId());
    }

    @Transactional
    public WmsImportJobResponse apply(UUID tenantId, UUID actorId, UUID jobId) {
        WmsImportJob job = jobService.getValidatedJobForTenant(tenantId, actorId, jobId);
        if (job.getImportType() != WmsImportType.SKU_CATALOG || job.getWarehouse() != null || job.getAudit() != null) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS,
                    "Import job không phải SKU catalog hợp lệ");
        }
        return jobService.applyJob(tenantId, actorId, jobId,
                lockedJob -> applyRows(tenantId, lockedJob));
    }

    private WmsImportJobResponse applyRows(UUID tenantId, WmsImportJob job) {
        List<fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow> rows =
                jobServiceRows(job.getId());
        Map<String, UUID> createdCategories = new HashMap<>();
        for (var row : rows) {
            if (row.getValidationErrors() != null && !row.getValidationErrors().isEmpty()) {
                continue;
            }
            Map<String, Object> payload = row.getNormalizedPayload();
            if ("CREATE".equals(payload.get("action"))) {
                CreateCategoryRequest request = CreateCategoryRequest.builder()
                        .name(string(payload.get("name")))
                        .defaultAttributes(asObjectMap(payload.get("default_attributes_json")))
                        .build();
                ProductCategoryResponse created = categoryService.createCategory(tenantId, request);
                if (payload.get("category_key") != null) {
                    createdCategories.put(string(payload.get("category_key")), created.getId());
                }
            }
        }
        for (var row : rows) {
            if (row.getValidationErrors() != null && !row.getValidationErrors().isEmpty()) {
                continue;
            }
            Map<String, Object> payload = row.getNormalizedPayload();
            String action = string(payload.get("action"));
            if ("SKIP".equals(action)) {
                continue;
            }
            UUID categoryId = resolveCategoryId(tenantId, payload, createdCategories);
            UnitOfMeasure uom = resolveUom(tenantId, string(payload.get("uom_code")));
            BigDecimal weight = decimal(payload.get("unit_weight_kg"));
            BigDecimal volume = decimal(payload.get("unit_volume_m3"));
            if ("CREATE".equals(action)) {
                skuService.createSku(tenantId, CreateSkuRequest.builder()
                        .categoryId(categoryId).skuCode(string(payload.get("sku_code")))
                        .name(string(payload.get("name"))).uomId(uom.getId())
                        .unitWeightKg(weight).unitVolumeM3(volume)
                        .specifications(asObjectMap(payload.get("specifications_json"))).build());
            } else if ("UPDATE".equals(action)) {
                UUID skuId = UUID.fromString(string(payload.get("sku_id")));
                skuService.updateSku(tenantId, skuId, UpdateSkuRequest.builder()
                        .categoryId(categoryId).name(string(payload.get("name"))).uomId(uom.getId())
                        .unitWeightKg(weight).unitVolumeM3(volume)
                        .specifications(asObjectMap(payload.get("specifications_json"))).build());
            }
        }
        return jobService.getJob(tenantId, tenantId, job.getId());
    }

    private List<fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow> jobServiceRows(UUID jobId) {
        // The job service deliberately owns the row repository in order to keep
        // row access centralized. This method is replaced by the package-level
        // row reader once feature-specific imports are added.
        return jobServiceRowsRepository().findByJobIdOrderBySheetNameAscRowNumberAsc(jobId);
    }

    private fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository jobServiceRowsRepository() {
        return rowRepository;
    }

    private final fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository rowRepository;

    private ParsedCatalog parse(UUID tenantId, byte[] bytes, String filename) {
        try (Workbook workbook = workbookReader.open(bytes, filename, Set.of("CATEGORIES", "SKUS"))) {
            Map<String, String> metadata = readMetadata(workbook);
            if (!SCHEMA_VERSION.equals(metadata.get("schema_version"))
                    || !"SKU_CATALOG".equals(metadata.get("workbook_type"))) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED);
            }
            Sheet categories = workbook.getSheet("CATEGORIES");
            Sheet skus = workbook.getSheet("SKUS");
            if (categories == null || skus == null) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "Workbook thiếu sheet CATEGORIES hoặc SKUS");
            }
            List<WmsImportRowInput> rows = new ArrayList<>();
            Map<String, Integer> categoryKeys = new HashMap<>();
            parseCategories(tenantId, categories, rows, categoryKeys);
            parseSkus(tenantId, skus, rows, categoryKeys);
            return new ParsedCatalog(rows);
        } catch (java.io.IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Không thể đọc workbook catalog");
        }
    }

    private void parseCategories(UUID tenantId, Sheet sheet, List<WmsImportRowInput> rows,
                                 Map<String, Integer> categoryKeys) {
        Map<String, Integer> headers = headers(sheet, CATEGORIES_HEADERS,
                "CATEGORIES", List.of("action", "category_key", "category_id", "source", "name", "default_attributes_json"));
        Set<String> seenKeys = new HashSet<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (blank(row, headers.values())) continue;
            Map<String, Object> payload = values(row, headers);
            List<Map<String, String>> errors = new ArrayList<>();
            String action = upper(payload.get("action"));
            String key = string(payload.get("category_key"));
            String source = upper(payload.get("source"));
            if (!Set.of("CREATE", "SKIP").contains(action)) addError(errors, "CATEGORY_ACTION", "action must be CREATE or SKIP");
            if (key.isBlank()) addError(errors, "CATEGORY_KEY_REQUIRED", "category_key is required");
            if (!key.isBlank() && !seenKeys.add(key) && "CREATE".equals(action)) addError(errors, "CATEGORY_KEY_DUPLICATE", "category_key is duplicated");
            if (!source.isBlank() && !Set.of("TENANT", "SYSTEM").contains(source)) addError(errors, "SOURCE_INVALID", "source must be TENANT or SYSTEM");
            if ("SYSTEM".equals(source) && !"SKIP".equals(action)) addError(errors, "SYSTEM_ROW_READ_ONLY", "system category cannot be changed");
            if ("CREATE".equals(action) && string(payload.get("name")).isBlank()) addError(errors, "CATEGORY_NAME_REQUIRED", "name is required for CREATE");
            parseJsonObject(payload, "default_attributes_json", errors);
            if ("CREATE".equals(action) && !key.isBlank()) categoryKeys.putIfAbsent(key, index);
            rows.add(new WmsImportRowInput("CATEGORIES", index + 1, key, payload, errors));
        }
    }

    private void parseSkus(UUID tenantId, Sheet sheet, List<WmsImportRowInput> rows,
                           Map<String, Integer> categoryKeys) {
        Map<String, Integer> headers = headers(sheet, SKUS_HEADERS, "SKUS",
                List.of("action", "sku_id", "source_updated_at", "source", "sku_code", "name",
                        "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3", "specifications_json"));
        Set<String> seenCodes = new HashSet<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (blank(row, headers.values())) continue;
            Map<String, Object> payload = values(row, headers);
            List<Map<String, String>> errors = new ArrayList<>();
            String action = upper(payload.get("action"));
            String source = upper(payload.get("source"));
            String skuCode = string(payload.get("sku_code"));
            if (!Set.of("CREATE", "UPDATE", "SKIP").contains(action)) addError(errors, "SKU_ACTION", "action must be CREATE, UPDATE or SKIP");
            if (skuCode.isBlank()) addError(errors, "SKU_CODE_REQUIRED", "sku_code is required");
            if (!skuCode.isBlank() && !"SKIP".equals(action) && !seenCodes.add(skuCode.toLowerCase(Locale.ROOT))) addError(errors, "SKU_CODE_DUPLICATE", "sku_code is duplicated");
            if ("SYSTEM".equals(source) && !"SKIP".equals(action)) addError(errors, "SYSTEM_ROW_READ_ONLY", "system SKU cannot be changed");
            if ("CREATE".equals(action) && !string(payload.get("sku_id")).isBlank()) addError(errors, "CREATE_ID_FORBIDDEN", "sku_id must be empty for CREATE");
            if ("UPDATE".equals(action)) validateExistingSkuUpdate(tenantId, payload, errors);
            if ("CREATE".equals(action) && !skuCode.isBlank()
                    && skuRepository.existsBySkuCodeAndTenantOrSystem(skuCode, tenantId)) {
                addError(errors, "SKU_CODE_EXISTS", "sku_code already exists for this tenant or system catalog");
            }
            validateCategoryReference(tenantId, payload, categoryKeys, errors);
            if (string(payload.get("uom_code")).isBlank()) addError(errors, "UOM_REQUIRED", "uom_code is required");
            else if (findUom(tenantId, string(payload.get("uom_code"))) == null) addError(errors, "UOM_NOT_VISIBLE", "uom_code is not visible to tenant");
            validatePositiveDecimal(payload, "unit_weight_kg", errors);
            validatePositiveDecimal(payload, "unit_volume_m3", errors);
            parseJsonObject(payload, "specifications_json", errors);
            rows.add(new WmsImportRowInput("SKUS", index + 1, skuCode, payload, errors));
        }
    }

    private void validateExistingSkuUpdate(UUID tenantId, Map<String, Object> payload, List<Map<String, String>> errors) {
        UUID id;
        try {
            id = UUID.fromString(string(payload.get("sku_id")));
        } catch (IllegalArgumentException ex) {
            addError(errors, "SKU_ID_INVALID", "sku_id must be a UUID for UPDATE");
            return;
        }
        ProductSku sku = skuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(id, tenantId).orElse(null);
        if (sku == null || sku.getTenant() == null || !tenantId.equals(sku.getTenant().getId())) {
            addError(errors, "SKU_CROSS_TENANT", "sku_id is not a tenant-owned SKU");
            return;
        }
        if (!sku.getSkuCode().equals(string(payload.get("sku_code")))) addError(errors, "SKU_CODE_IMMUTABLE", "sku_code cannot change on UPDATE");
        LocalDateTime sourceUpdated = dateTime(payload.get("source_updated_at"));
        if (sourceUpdated == null || sku.getUpdatedAt() == null || !sourceUpdated.equals(sku.getUpdatedAt())) {
            addError(errors, "SKU_STALE", "source_updated_at does not match current SKU");
        }
    }

    private void validateCategoryReference(UUID tenantId, Map<String, Object> payload,
                                           Map<String, Integer> categoryKeys, List<Map<String, String>> errors) {
        String id = string(payload.get("category_id"));
        String key = string(payload.get("category_key"));
        if (!id.isBlank() && !key.isBlank()) addError(errors, "CATEGORY_REFERENCE_AMBIGUOUS", "Use category_id or category_key, not both");
        if (!id.isBlank()) {
            try {
                ProductCategory category = categoryRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(UUID.fromString(id), tenantId).orElse(null);
                if (category == null) addError(errors, "CATEGORY_NOT_VISIBLE", "category_id is not visible to tenant");
            } catch (IllegalArgumentException ex) {
                addError(errors, "CATEGORY_ID_INVALID", "category_id must be a UUID");
            }
        } else if (!key.isBlank() && !categoryKeys.containsKey(key)) {
            addError(errors, "CATEGORY_KEY_NOT_FOUND", "category_key does not reference a CREATE row");
        }
    }

    private Map<String, Integer> headers(Sheet sheet, Set<String> expected, String sheetName, List<String> ordered) {
        Row row = sheet.getRow(0);
        if (row == null) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, sheetName + " header is missing");
        Map<String, Integer> result = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String value = formatter.formatCellValue(row.getCell(i)).trim();
            if (!value.isBlank() && !duplicates.add(value)) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, sheetName + " has duplicate header: " + value);
            if (expected.contains(value)) result.put(value, i);
        }
        if (!result.keySet().equals(new HashSet<>(expected))) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, sheetName + " headers do not match template");
        Map<String, Integer> orderedResult = new LinkedHashMap<>();
        ordered.forEach(name -> orderedResult.put(name, result.get(name)));
        return orderedResult;
    }

    private Map<String, Object> values(Row row, Map<String, Integer> headers) {
        DataFormatter formatter = new DataFormatter();
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach((name, index) -> {
            String value = formatter.formatCellValue(row.getCell(index)).trim();
            if (value.length() > properties.getMaxTextCellLength()) throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED, "Cell value quá dài tại " + row.getRowNum());
            values.put(name, value);
        });
        return values;
    }

    private boolean blank(Row row, java.util.Collection<Integer> columns) {
        if (row == null) return true;
        DataFormatter formatter = new DataFormatter();
        return columns.stream().allMatch(column -> formatter.formatCellValue(row.getCell(column)).trim().isBlank());
    }

    private Map<String, String> readMetadata(Workbook workbook) {
        Sheet sheet = workbook.getSheet("_META");
        if (sheet == null) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, "Workbook thiếu _META");
        DataFormatter formatter = new DataFormatter();
        Map<String, String> metadata = new HashMap<>();
        for (Row row : sheet) {
            if (row.getLastCellNum() >= 2) metadata.put(formatter.formatCellValue(row.getCell(0)), formatter.formatCellValue(row.getCell(1)));
        }
        return metadata;
    }

    private void parseJsonObject(Map<String, Object> payload, String field, List<Map<String, String>> errors) {
        String value = string(payload.get(field));
        if (value.isBlank()) return;
        if (value.length() > properties.getMaxJsonCellLength()) {
            addError(errors, "JSON_TOO_LARGE", field + " exceeds the configured length");
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isObject()) addError(errors, "JSON_OBJECT_REQUIRED", field + " must be a JSON object");
        } catch (JsonProcessingException ex) {
            addError(errors, "JSON_INVALID", field + " is not valid JSON");
        }
    }

    private void validatePositiveDecimal(Map<String, Object> payload, String field, List<Map<String, String>> errors) {
        try {
            if (decimal(payload.get(field)).signum() <= 0) addError(errors, "DECIMAL_POSITIVE_REQUIRED", field + " must be greater than 0");
        } catch (IllegalArgumentException ex) {
            addError(errors, "DECIMAL_INVALID", field + " must be a decimal number");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID);
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "Không thể đọc file upload");
        }
    }

    private UUID resolveCategoryId(UUID tenantId, Map<String, Object> payload, Map<String, UUID> createdCategories) {
        String id = string(payload.get("category_id"));
        if (!id.isBlank()) return UUID.fromString(id);
        String key = string(payload.get("category_key"));
        if (key.isBlank()) return null;
        UUID created = createdCategories.get(key);
        if (created == null) throw new BadRequestException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
        return created;
    }

    private UnitOfMeasure resolveUom(UUID tenantId, String code) {
        UnitOfMeasure uom = findUom(tenantId, code);
        if (uom == null) throw new ResourceNotFoundException(ErrorCode.UOM_NOT_FOUND);
        return uom;
    }

    private UnitOfMeasure findUom(UUID tenantId, String code) {
        return uomRepository.findAllActiveByTenantOrSystem(tenantId,
                        org.springframework.data.domain.PageRequest.of(0, 1000)).stream()
                .filter(item -> item.getCode().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private Map<String, Object> asObjectMap(Object value) {
        String text = string(value);
        if (text.isBlank()) return null;
        try {
            return objectMapper.convertValue(objectMapper.readTree(text), Map.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "JSON object không hợp lệ");
        }
    }

    private BigDecimal decimal(Object value) {
        String text = string(value);
        if (text.isBlank()) throw new IllegalArgumentException("decimal is blank");
        return new BigDecimal(text);
    }

    private LocalDateTime dateTime(Object value) {
        String text = string(value);
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String upper(Object value) {
        return string(value).toUpperCase(Locale.ROOT);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void addError(List<Map<String, String>> errors, String code, String message) {
        errors.add(Map.of("code", code, "message", message));
    }

    private record ParsedCatalog(List<WmsImportRowInput> rows) {
    }
}
