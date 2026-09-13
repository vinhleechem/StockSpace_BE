package fu.stockspace.stockspace_be.wms.dataexchange.audit;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobCommand;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowInput;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.AddUnexpectedAuditItemRequest;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.SaveAuditCountItemRequest;
import fu.stockspace.stockspace_be.wms.stock.dto.SaveAuditCountsRequest;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates and applies audit count workbooks through the canonical audit workflow. */
@Service
@RequiredArgsConstructor
public class AuditReconciliationImportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String WORKBOOK_TYPE = "AUDIT_COUNT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> COUNT_HEADERS = List.of("audit_item_id", "sku_code", "sku_name", "uom_code",
            "rack_code", "rack_name", "bin_code", "bin_name", "shelf_level", "actual_quantity", "note", "variance_reason");
    private static final Set<String> COUNT_HEADER_SET = Set.copyOf(COUNT_HEADERS);
    private static final List<String> UNEXPECTED_HEADERS = List.of("sku_code", "rack_code", "bin_code", "actual_quantity", "note");
    private static final Set<String> UNEXPECTED_HEADER_SET = Set.copyOf(UNEXPECTED_HEADERS);

    private final XlsxWorkbookReader workbookReader;
    private final CanonicalContentHashService hashService;
    private final WmsImportJobService jobService;
    private final WmsImportJobRepository jobRepository;
    private final WmsImportRowRepository rowRepository;
    private final DataExchangeProperties properties;
    private final InventoryAuditRepository auditRepository;
    private final InventoryAuditItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final ProductSkuRepository skuRepository;
    private final InventoryAuditService auditService;

    @Transactional
    public WmsImportJobResponse validate(UUID actorId, UUID auditId, MultipartFile file) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        InventoryAudit audit = auditRepository.findById(auditId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
        UUID tenantId = resolveTenantId(actor, audit);
        requireCountAccess(actor, tenantId, audit);
        requireCountStatus(audit);
        byte[] bytes = readBytes(file);
        ParsedWorkbook parsed = parse(actor, tenantId, audit, bytes, file.getOriginalFilename());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("audit_id", auditId);
        context.put("warehouse_id", audit.getWarehouse().getId());
        context.put("count_round", audit.getCountRound());
        context.put("audit_version", audit.getVersion());
        context.put("audit_status", audit.getStatus());
        context.put("generated_at", parsed.generatedAt().toString());
        WmsImportJob job = jobService.createJob(new WmsImportJobCommand(
                tenantId, actorId, audit.getWarehouse().getId(), auditId, WmsImportType.AUDIT_RECONCILIATION,
                SCHEMA_VERSION, file.getOriginalFilename(), hashService.sha256(bytes), context, parsed.rows()));
        return jobService.getJob(tenantId, actorId, job.getId());
    }

    @Transactional
    public AuditReconciliationApplyResponse apply(UUID actorId, UUID jobId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantIdFromJob(actor, jobId);
        jobService.applyJob(tenantId, actorId, jobId, job -> applyRows(actorId, tenantId, actor, job));
        WmsImportJobResponse job = jobService.getJob(tenantId, actorId, jobId);
        UUID auditId = UUID.fromString(String.valueOf(job.contextMetadata().get("audit_id")));
        InventoryAuditResponse audit = auditService.getAuditDetail(actorId, auditId);
        return new AuditReconciliationApplyResponse(job, audit);
    }

    private Object applyRows(UUID actorId, UUID tenantId, User actor, WmsImportJob job) {
        UUID auditId = uuid(job.getContextMetadata(), "audit_id");
        InventoryAudit audit = auditRepository.findByIdForUpdate(auditId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
        assertCurrentMetadata(tenantId, job, audit);
        requireCountAccess(actor, tenantId, audit);
        requireCountStatus(audit);

        List<WmsImportRow> rows = rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(job.getId());
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.getValidationErrors() != null
                && !row.getValidationErrors().isEmpty())) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS);
        }

        List<SaveAuditCountItemRequest> countItems = rows.stream()
                .filter(row -> "COUNT_ITEMS".equals(row.getSheetName()))
                .map(row -> toCountRequest(row, audit))
                .toList();
        List<WmsImportRow> unexpectedRows = rows.stream()
                .filter(row -> "UNEXPECTED_ITEMS".equals(row.getSheetName()))
                .toList();
        for (WmsImportRow row : unexpectedRows) {
            AddUnexpectedAuditItemRequest request = toUnexpectedRequest(row, audit, tenantId);
            auditService.addUnexpectedItem(actorId, auditId, request);
        }
        auditService.saveAuditCounts(actorId, auditId, new SaveAuditCountsRequest(countItems));
        for (WmsImportRow row : rows) {
            row.setResultResourceType("INVENTORY_AUDIT");
            row.setResultResourceId(auditId);
        }
        rowRepository.saveAll(rows);
        return Boolean.TRUE;
    }

    private ParsedWorkbook parse(User actor, UUID tenantId, InventoryAudit audit,
                                 byte[] bytes, String filename) {
        try (Workbook workbook = workbookReader.open(bytes, filename,
                Set.of("COUNT_ITEMS", "UNEXPECTED_ITEMS"))) {
            Map<String, String> metadata = readMetadata(workbook);
            validateMetadata(metadata, tenantId, audit);
            LocalDateTime generatedAt = parseTimestamp(metadata.get("generated_at"));
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (generatedAt == null || generatedAt.isAfter(now)) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "generated_at không hợp lệ");
            }
            Sheet countSheet = workbook.getSheet("COUNT_ITEMS");
            Sheet unexpectedSheet = workbook.getSheet("UNEXPECTED_ITEMS");
            if (countSheet == null || unexpectedSheet == null) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "Workbook thiếu COUNT_ITEMS hoặc UNEXPECTED_ITEMS");
            }
            Map<String, Integer> countHeaders = headers(countSheet, COUNT_HEADER_SET, COUNT_HEADERS, "COUNT_ITEMS");
            Map<String, Integer> unexpectedHeaders = headers(unexpectedSheet, UNEXPECTED_HEADER_SET,
                    UNEXPECTED_HEADERS, "UNEXPECTED_ITEMS");
            List<WmsImportRowInput> result = new ArrayList<>();
            Set<UUID> expectedItemIds = itemRepository.findByAuditIdAndCountRoundOrderById(
                            audit.getId(), audit.getCountRound()).stream()
                    .map(InventoryAuditItem::getId).collect(java.util.stream.Collectors.toSet());
            Set<UUID> submittedItemIds = new HashSet<>();
            for (int index = 1; index <= countSheet.getLastRowNum(); index++) {
                Row row = countSheet.getRow(index);
                if (blank(row, countHeaders.values())) continue;
                Map<String, Object> payload = values(row, countHeaders);
                List<Map<String, String>> errors = new ArrayList<>();
                validateCountRow(payload, actor, tenantId, audit, expectedItemIds, submittedItemIds, errors);
                result.add(new WmsImportRowInput("COUNT_ITEMS", index + 1,
                        string(payload.get("audit_item_id")), payload, errors));
            }
            if (!submittedItemIds.equals(expectedItemIds)) {
                Set<UUID> missing = new HashSet<>(expectedItemIds);
                missing.removeAll(submittedItemIds);
                if (result.isEmpty()) {
                    result.add(new WmsImportRowInput("COUNT_ITEMS", 2, "",
                            Map.of(), List.of(error("COUNT_ITEMS_REQUIRED", "COUNT_ITEMS phải có đủ item của count round hiện tại"))));
                } else {
                    result.forEach(row -> addError(row.validationErrors(), "COUNT_ITEMS_INCOMPLETE",
                            "COUNT_ITEMS thiếu item: " + missing));
                }
            }
            Set<String> unexpectedKeys = new HashSet<>();
            for (int index = 1; index <= unexpectedSheet.getLastRowNum(); index++) {
                Row row = unexpectedSheet.getRow(index);
                if (blank(row, unexpectedHeaders.values())) continue;
                Map<String, Object> payload = values(row, unexpectedHeaders);
                List<Map<String, String>> errors = new ArrayList<>();
                validateUnexpectedRow(payload, tenantId, audit, unexpectedKeys, errors);
                result.add(new WmsImportRowInput("UNEXPECTED_ITEMS", index + 1,
                        unexpectedKey(payload), payload, errors));
            }
            if (result.size() > properties.getMaxRows()) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED);
            }
            return new ParsedWorkbook(result, generatedAt);
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Không thể đọc workbook audit count");
        }
    }

    private void validateMetadata(Map<String, String> metadata, UUID tenantId, InventoryAudit audit) {
        if (!SCHEMA_VERSION.equals(metadata.get("schema_version"))
                || !WORKBOOK_TYPE.equals(metadata.get("workbook_type"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED);
        }
        if (!tenantId.toString().equalsIgnoreCase(metadata.get("tenant_id"))
                || !audit.getId().toString().equalsIgnoreCase(metadata.get("audit_id"))
                || !audit.getWarehouse().getId().toString().equalsIgnoreCase(metadata.get("warehouse_id"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "Workbook không thuộc audit/warehouse hiện tại");
        }
        if (!String.valueOf(audit.getCountRound()).equals(metadata.get("count_round"))
                || !String.valueOf(audit.getVersion()).equals(metadata.get("audit_version"))
                || !String.valueOf(audit.getStatus()).equals(metadata.get("audit_status"))) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
        if (!BUSINESS_ZONE.getId().equals(metadata.get("timezone"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "Workbook phải dùng timezone Asia/Ho_Chi_Minh");
        }
    }

    private void validateCountRow(Map<String, Object> payload, User actor, UUID tenantId,
                                  InventoryAudit audit, Set<UUID> expectedIds, Set<UUID> submittedIds,
                                  List<Map<String, String>> errors) {
        UUID itemId = parseUuid(payload.get("audit_item_id"));
        if (itemId == null || !expectedIds.contains(itemId)) {
            addError(errors, "AUDIT_ITEM_INVALID", "audit_item_id không thuộc count round hiện tại");
            return;
        }
        if (!submittedIds.add(itemId)) addError(errors, "AUDIT_ITEM_DUPLICATE", "audit_item_id bị lặp");
        InventoryAuditItem item = itemRepository.findById(itemId).orElse(null);
        ProductSku sku = item == null ? null : skuFor(item);
        WarehouseRack rack = item == null ? null : item.getBatch() != null ? item.getBatch().getRack() : item.getRack();
        WarehouseBin bin = item == null ? null : item.getBatch() != null ? item.getBatch().getBin() : item.getBin();
        if (sku == null || !sku.getSkuCode().equalsIgnoreCase(string(payload.get("sku_code")))) {
            addError(errors, "AUDIT_ITEM_SKU_CHANGED", "sku_code không khớp audit item");
        }
        if (!sameCode(rack, string(payload.get("rack_code")))
                || !sameCode(bin, string(payload.get("bin_code")))) {
            addError(errors, "AUDIT_ITEM_LOCATION_CHANGED", "location của audit item không được thay đổi");
        }
        int actual = integer(payload.get("actual_quantity"), errors, "actual_quantity");
        if (actual < 0) addError(errors, "ACTUAL_QUANTITY_NEGATIVE", "actual_quantity phải lớn hơn hoặc bằng 0");
        if (string(payload.get("note")).length() > 2000 || string(payload.get("variance_reason")).length() > 2000) {
            addError(errors, "TEXT_TOO_LONG", "note và variance_reason không quá 2000 ký tự");
        }
    }

    private void validateUnexpectedRow(Map<String, Object> payload, UUID tenantId, InventoryAudit audit,
                                       Set<String> seen, List<Map<String, String>> errors) {
        String skuCode = string(payload.get("sku_code"));
        String rackCode = string(payload.get("rack_code"));
        String binCode = string(payload.get("bin_code"));
        if (skuCode.isBlank() || rackCode.isBlank() || binCode.isBlank()) {
            addError(errors, "UNEXPECTED_LOCATION_REQUIRED", "unexpected item cần sku_code, rack_code và bin_code");
            return;
        }
        ProductSku sku = findSku(tenantId, skuCode);
        if (sku == null) addError(errors, "SKU_NOT_VISIBLE", "sku_code không visible với tenant");
        Location location = findLocation(audit, rackCode, binCode);
        if (location == null) addError(errors, "LOCATION_NOT_IN_SCOPE", "location không thuộc warehouse hoặc audit scope");
        int actual = integer(payload.get("actual_quantity"), errors, "actual_quantity");
        if (actual < 0) addError(errors, "ACTUAL_QUANTITY_NEGATIVE", "actual_quantity phải lớn hơn hoặc bằng 0");
        String key = (skuCode + "|" + rackCode + "|" + binCode).toLowerCase(Locale.ROOT);
        if (!seen.add(key)) addError(errors, "UNEXPECTED_DUPLICATE", "SKU + rack + bin bị lặp");
        if (location != null && sku != null) {
            payload.put("sku_id", sku.getId());
            payload.put("rack_id", location.rackId());
            payload.put("bin_id", location.binId());
        }
    }

    private SaveAuditCountItemRequest toCountRequest(WmsImportRow row, InventoryAudit audit) {
        Map<String, Object> payload = row.getNormalizedPayload();
        return SaveAuditCountItemRequest.builder()
                .itemId(uuid(payload, "audit_item_id"))
                .actualQuantity(positiveOrZero(payload.get("actual_quantity")))
                .note(string(payload.get("note")))
                .varianceReason(string(payload.get("variance_reason")))
                .build();
    }

    private AddUnexpectedAuditItemRequest toUnexpectedRequest(WmsImportRow row, InventoryAudit audit, UUID tenantId) {
        Map<String, Object> payload = row.getNormalizedPayload();
        return AddUnexpectedAuditItemRequest.builder()
                .skuId(uuid(payload, "sku_id"))
                .rackId(uuid(payload, "rack_id"))
                .binId(uuid(payload, "bin_id"))
                .actualQuantity(positiveOrZero(payload.get("actual_quantity")))
                .note(string(payload.get("note")))
                .build();
    }

    private void assertCurrentMetadata(UUID tenantId, WmsImportJob job, InventoryAudit audit) {
        Map<String, Object> metadata = job.getContextMetadata();
        if (!tenantId.equals(auditTenantId(audit))
                || !audit.getWarehouse().getId().equals(job.getWarehouse().getId())
                || !audit.getId().equals(uuid(metadata, "audit_id"))
                || audit.getCountRound() != integer(metadata.get("count_round"))
                || !java.util.Objects.equals(audit.getVersion(), longValue(metadata.get("audit_version")))
                || audit.getStatus().name().equals(string(metadata.get("audit_status"))) == false) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
    }

    private UUID resolveTenantIdFromJob(User actor, UUID jobId) {
        WmsImportJob job = jobRepository.findById(jobId)
                .filter(item -> item.isActive() && !item.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND));
        if (job.getTenant() == null) throw new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND);
        UUID tenantId = auditTenantForActor(actor);
        if (!tenantId.equals(job.getTenant().getId())) {
            throw new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND);
        }
        return tenantId;
    }

    private UUID resolveTenantId(User actor, InventoryAudit audit) {
        UUID tenantId = auditTenantId(audit);
        UUID actorTenant = auditTenantForActor(actor);
        if (!tenantId.equals(actorTenant)) throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        return tenantId;
    }

    private UUID auditTenantForActor(User actor) {
        return actor.getRoles().stream()
                .filter(role -> fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name().equals(role.getName()))
                .findAny()
                .map(ignored -> tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(actor.getId())
                        .map(member -> member.getTenant().getId())
                        .orElseThrow(() -> new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE)))
                .orElse(actor.getId());
    }

    private UUID auditTenantId(InventoryAudit audit) {
        return audit.getTenant() != null ? audit.getTenant().getId() : audit.getRequestedBy().getId();
    }

    private void requireCountAccess(User actor, UUID tenantId, InventoryAudit audit) {
        accessService.requireActiveContract(tenantId, audit.getWarehouse().getId());
        if (actor.getRoles().stream().anyMatch(role -> fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name().equals(role.getName()))) {
            accessService.requireActiveStaffAssignment(actor.getId(), tenantId, audit.getWarehouse().getId());
            if (audit.getAssignedTo() == null || !actor.getId().equals(audit.getAssignedTo().getId())) {
                throw new fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private void requireCountStatus(InventoryAudit audit) {
        if (audit.getStatus() != AuditStatus.IN_PROGRESS && audit.getStatus() != AuditStatus.REOPENED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
    }

    private Location findLocation(InventoryAudit audit, String rackCode, String binCode) {
        UUID tenantId = auditTenantId(audit);
        WarehouseLayout layout = layoutRepository.findByWarehouseIdAndTenantId(audit.getWarehouse().getId(), tenantId)
                .filter(item -> item.isActive() && !item.isDeleted()).orElse(null);
        if (layout == null) return null;
        WarehouseRack rack = rackRepository.findAllByLayoutId(layout.getId()).stream()
                .filter(item -> item.isActive() && !item.isDeleted())
                .filter(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(rackCode))
                .findFirst().orElse(null);
        if (rack == null) return null;
        WarehouseBin bin = binRepository.findAllByRackId(rack.getId()).stream()
                .filter(item -> item.isActive() && !item.isDeleted())
                .filter(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(binCode))
                .findFirst().orElse(null);
        if (bin == null || !inScope(audit, rack, bin)) return null;
        return new Location(rack.getId(), bin.getId());
    }

    private boolean inScope(InventoryAudit audit, WarehouseRack rack, WarehouseBin bin) {
        if (audit.getScopeType() == null || audit.getScopeType() == AuditScopeType.WAREHOUSE) return true;
        if (audit.getScopeType() == AuditScopeType.RACK) {
            return audit.getScopeRack() != null && rack != null && audit.getScopeRack().getId().equals(rack.getId());
        }
        return audit.getScopeBin() != null && bin != null && audit.getScopeBin().getId().equals(bin.getId());
    }

    private ProductSku findSku(UUID tenantId, String code) {
        return skuRepository.findVisibleActiveByTenantAndSkuCodes(tenantId,
                Set.of(code.toLowerCase(Locale.ROOT))).stream().findFirst().orElse(null);
    }

    private ProductSku skuFor(InventoryAuditItem item) {
        UUID skuId = item.getBatch() != null ? item.getBatch().getSkuId() : item.getSkuId();
        return skuId == null ? null : skuRepository.findByIdAndIsDeletedFalse(skuId).orElse(null);
    }

    private boolean sameCode(WarehouseRack rack, String code) {
        return rack != null && rack.getCode() != null && rack.getCode().equalsIgnoreCase(code);
    }

    private boolean sameCode(WarehouseBin bin, String code) {
        return bin != null && bin.getCode() != null && bin.getCode().equalsIgnoreCase(code);
    }

    private Map<String, Integer> headers(Sheet sheet, Set<String> expected, List<String> ordered, String name) {
        Row header = sheet.getRow(0);
        if (header == null) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                name + " header is missing");
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> found = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String value = formatter.formatCellValue(header.getCell(index)).trim();
            if (!value.isBlank() && !duplicates.add(value)) throw new BadRequestException(
                    ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, name + " has duplicate header");
            if (expected.contains(value)) found.put(value, index);
        }
        if (!found.keySet().equals(expected)) throw new BadRequestException(
                ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED, name + " headers do not match template");
        Map<String, Integer> result = new LinkedHashMap<>();
        ordered.forEach(value -> result.put(value, found.get(value)));
        return result;
    }

    private Map<String, Object> values(Row row, Map<String, Integer> headers) {
        DataFormatter formatter = new DataFormatter();
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach((name, index) -> {
            String value = formatter.formatCellValue(row.getCell(index)).trim();
            if (value.length() > properties.getMaxTextCellLength()) throw new BadRequestException(
                    ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED, "Cell value quá dài");
            values.put(name, value);
        });
        return values;
    }

    private boolean blank(Row row, Collection<Integer> columns) {
        if (row == null) return true;
        DataFormatter formatter = new DataFormatter();
        return columns.stream().allMatch(column -> formatter.formatCellValue(row.getCell(column)).trim().isBlank());
    }

    private Map<String, String> readMetadata(Workbook workbook) {
        Sheet sheet = workbook.getSheet("_META");
        if (sheet == null) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                "Workbook thiếu _META");
        DataFormatter formatter = new DataFormatter();
        Map<String, String> metadata = new HashMap<>();
        for (Row row : sheet) if (row.getLastCellNum() >= 2) metadata.put(
                formatter.formatCellValue(row.getCell(0)).trim(), formatter.formatCellValue(row.getCell(1)).trim());
        return metadata;
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID);
        try { return file.getBytes(); } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "Không thể đọc file upload");
        }
    }

    private LocalDateTime parseTimestamp(Object value) {
        String text = string(value);
        try { return ZonedDateTime.parse(text).withZoneSameInstant(BUSINESS_ZONE).toLocalDateTime(); }
        catch (DateTimeParseException ignored) {
            try { return OffsetDateTime.parse(text).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime(); }
            catch (DateTimeParseException ignoredAgain) {
                try { return LocalDateTime.parse(text); }
                catch (DateTimeParseException ignoredFinal) { return null; }
            }
        }
    }

    private int integer(Object value, List<Map<String, String>> errors, String field) {
        try { return new BigDecimal(string(value)).intValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) {
            addError(errors, field.toUpperCase(Locale.ROOT) + "_INVALID", field + " phải là số nguyên");
            return 0;
        }
    }

    private int integer(Object value) {
        try { return new BigDecimal(string(value)).intValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) { throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE); }
    }

    private int positiveOrZero(Object value) {
        int result = integer(value);
        if (result < 0) throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        return result;
    }

    private UUID parseUuid(Object value) {
        try { return UUID.fromString(string(value)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private UUID uuid(Map<String, Object> values, String key) {
        UUID result = parseUuid(values.get(key));
        if (result == null) throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        return result;
    }

    private UUID uuid(Map<String, Object> values, String key, boolean nullable) {
        UUID result = parseUuid(values.get(key));
        if (result == null && !nullable) throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        return result;
    }

    private Long longValue(Object value) {
        try { return Long.valueOf(string(value)); }
        catch (NumberFormatException ex) { return null; }
    }

    private String unexpectedKey(Map<String, Object> payload) {
        return (string(payload.get("sku_code")) + "|" + string(payload.get("rack_code")) + "|"
                + string(payload.get("bin_code"))).toLowerCase(Locale.ROOT);
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private void addError(List<Map<String, String>> errors, String code, String message) { errors.add(error(code, message)); }

    private Map<String, String> error(String code, String message) { return Map.of("code", code, "message", message); }

    private record ParsedWorkbook(List<WmsImportRowInput> rows, LocalDateTime generatedAt) { }

    private record Location(UUID rackId, UUID binId) { }
}
