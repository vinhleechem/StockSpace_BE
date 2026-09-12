package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
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
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowInput;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookReader;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;
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

/**
 * Creates the controlled workbook used for offline receipt movements and
 * validates it without creating receipts. Applying the resulting job is a
 * separate, tenant-only operation in the receipt orchestration service.
 */
@Service
@RequiredArgsConstructor
public class OfflineMovementWorkbookService {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String WORKBOOK_TYPE = "OFFLINE_MOVEMENT";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<String> MOVEMENT_HEADERS = List.of(
            "movement_ref", "sequence_no", "type", "occurred_at", "sender_name", "receiver_name",
            "sku_code", "quantity", "rack_code", "bin_code", "note");
    private static final Set<String> MOVEMENT_HEADER_SET = Set.copyOf(MOVEMENT_HEADERS);

    private final XlsxWorkbookReader workbookReader;
    private final CanonicalContentHashService hashService;
    private final WmsImportJobService jobService;
    private final DataExchangeProperties properties;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final ProductSkuRepository skuRepository;
    private final TenantWarehouseAccessService accessService;
    private final StockFingerprintService fingerprintService;

    @Transactional(readOnly = true)
    public byte[] renderTemplate(UUID tenantId, UUID actorId, UUID warehouseId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        Warehouse warehouse = activeWarehouse(warehouseId);
        requireObservation(actor, tenantId, warehouseId);

        WarehouseLayout layout = activeTenantLayout(warehouseId, tenantId);
        List<WarehouseRack> racks = rackRepository.findAllByLayoutId(layout.getId()).stream()
                .filter(this::activeRack)
                .sorted(Comparator.comparing(WarehouseRack::getCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<WarehouseBin> bins = racks.stream()
                .flatMap(rack -> binRepository.findAllByRackId(rack.getId()).stream())
                .filter(this::activeBin)
                .sorted(Comparator.comparing(WarehouseBin::getCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<ProductSku> skus = skuRepository.findAllActiveByTenantOrSystem(tenantId,
                        PageRequest.of(0, properties.getMaxRows()))
                .getContent().stream()
                .filter(ProductSku::isActive)
                .sorted(Comparator.comparing(ProductSku::getSkuCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        if (skus.size() >= properties.getMaxRows()) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                    "SKU lookup vượt quá giới hạn workbook");
        }

        ZonedDateTime generatedAt = ZonedDateTime.now(BUSINESS_ZONE);
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", SCHEMA_VERSION);
            metadata.put("workbook_type", WORKBOOK_TYPE);
            metadata.put("export_id", UUID.randomUUID());
            metadata.put("generated_at", generatedAt);
            metadata.put("tenant_id", tenantId);
            metadata.put("warehouse_id", warehouseId);
            metadata.put("warehouse_name", warehouse.getName());
            metadata.put("stock_fingerprint", fingerprintService.fingerprint(warehouseId));
            metadata.put("timezone", BUSINESS_ZONE.getId());
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);
            writeReadme(workbook, generatedAt, warehouse);
            writeMovements(workbook);
            writeSkuLookup(workbook, skus);
            writeLocationLookup(workbook, racks, bins);
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (IOException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Unable to render offline movement template", ex);
        }
    }

    @Transactional
    public WmsImportJobResponse validate(UUID tenantId, UUID actorId, UUID warehouseId,
                                         MultipartFile file) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        activeWarehouse(warehouseId);
        requireWmsAccess(actor, tenantId, warehouseId);
        byte[] bytes = readBytes(file);
        ParsedWorkbook parsed = parse(tenantId, actor, warehouseId, bytes, file.getOriginalFilename());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("generated_at", parsed.generatedAt().toString());
        context.put("stock_fingerprint", parsed.stockFingerprint());
        context.put("timezone", BUSINESS_ZONE.getId());
        context.put("movement_group_count", parsed.groupCount());
        WmsImportJob job = jobService.createJob(new WmsImportJobCommand(
                tenantId, actorId, warehouseId, null, WmsImportType.OFFLINE_MOVEMENT,
                SCHEMA_VERSION, file.getOriginalFilename(), hashService.sha256(bytes), context, parsed.rows()));
        return jobService.getJob(tenantId, actorId, job.getId());
    }

    private void writeReadme(Workbook workbook, ZonedDateTime generatedAt, Warehouse warehouse) {
        Sheet sheet = workbook.createSheet("README");
        sheet.createRow(0).createCell(0).setCellValue("StockSpace Offline Movement Workbook v1.0");
        sheet.createRow(1).createCell(0).setCellValue("Generated at: " + generatedAt);
        sheet.createRow(2).createCell(0).setCellValue("Warehouse: " + XlsxWorkbookWriter.safeText(warehouse.getName()));
        sheet.createRow(4).createCell(0).setCellValue("Fill MOVEMENTS only. One movement_ref may contain multiple SKU rows.");
        sheet.createRow(5).createCell(0).setCellValue("INBOUND requires rack_code and bin_code. OUTBOUND leaves both blank for FIFO or fills both for manual location FIFO.");
        sheet.createRow(6).createCell(0).setCellValue("Do not change _META, SKU_LOOKUP or LOCATION_LOOKUP. Validation never creates receipts.");
        sheet.createRow(7).createCell(0).setCellValue("occurred_at must use ISO-8601 time in Asia/Ho_Chi_Minh and be between generated_at and upload time.");
        sheet.setColumnWidth(0, 120 * 256);
    }

    private void writeMovements(Workbook workbook) {
        Sheet sheet = workbook.createSheet("MOVEMENTS");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle editable = XlsxWorkbookWriter.editableStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, MOVEMENT_HEADERS.toArray(String[]::new));
        Row entry = sheet.createRow(1);
        for (int column = 0; column < MOVEMENT_HEADERS.size(); column++) {
            entry.createCell(column).setCellStyle(editable);
        }
        finishSheet(sheet, MOVEMENT_HEADERS.size());
    }

    private void writeSkuLookup(Workbook workbook, List<ProductSku> skus) {
        Sheet sheet = workbook.createSheet("SKU_LOOKUP");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        List<String> headers = List.of("sku_code", "sku_name", "uom_code", "unit_weight_kg",
                "unit_volume_m3", "category_name");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        int rowIndex = 1;
        for (ProductSku sku : skus) {
            Row row = sheet.createRow(rowIndex++);
            Object[] values = {sku.getSkuCode(), sku.getName(), sku.getUom() == null ? null : sku.getUom().getCode(),
                    sku.getUnitWeightKg(), sku.getUnitVolumeM3(), sku.getCategory() == null ? null : sku.getCategory().getName()};
            writeValues(row, values, readOnly);
        }
        finishSheet(sheet, headers.size());
    }

    private void writeLocationLookup(Workbook workbook, List<WarehouseRack> racks,
                                     List<WarehouseBin> bins) {
        Sheet sheet = workbook.createSheet("LOCATION_LOOKUP");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        List<String> headers = List.of("rack_code", "rack_name", "shelf_count", "max_bin_count",
                "rack_width", "rack_length", "rack_height", "bin_code", "bin_name", "shelf_level",
                "bin_coordinate_x", "bin_coordinate_y", "bin_position_z", "bin_width", "bin_length",
                "bin_height", "bin_max_weight", "bin_max_volume");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        Map<UUID, WarehouseRack> racksById = racks.stream()
                .collect(java.util.stream.Collectors.toMap(WarehouseRack::getId, rack -> rack));
        int rowIndex = 1;
        for (WarehouseBin bin : bins) {
            WarehouseRack rack = racksById.get(bin.getRack().getId());
            Object[] values = {rack.getCode(), rack.getName(), rack.getShelfCount(), rack.getMaxBinCount(),
                    rack.getWidth(), rack.getLength(), rack.getHeight(), bin.getCode(), bin.getName(),
                    bin.getShelfLevel(), bin.getCoordinateX(), bin.getCoordinateY(), bin.getPositionZ(),
                    bin.getWidth(), bin.getLength(), bin.getHeight(), bin.getMaxWeight(), bin.getMaxVolume()};
            writeValues(sheet.createRow(rowIndex++), values, readOnly);
        }
        finishSheet(sheet, headers.size());
    }

    private void writeValues(Row row, Object[] values, CellStyle style) {
        for (int column = 0; column < values.length; column++) {
            var cell = row.createCell(column);
            Object value = values[column];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(XlsxWorkbookWriter.safeText(value));
            }
            cell.setCellStyle(style);
        }
    }

    private void finishSheet(Sheet sheet, int columnCount) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, sheet.getLastRowNum()), 0, columnCount - 1));
        sheet.protectSheet("wms-data");
        for (int column = 0; column < columnCount; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    private ParsedWorkbook parse(UUID tenantId, User actor, UUID warehouseId,
                                 byte[] bytes, String filename) {
        try (Workbook workbook = workbookReader.open(bytes, filename, Set.of("MOVEMENTS"))) {
            Map<String, String> metadata = readMetadata(workbook);
            validateMetadata(metadata, tenantId, warehouseId);
            LocalDateTime generatedAt = parseTimestamp(metadata.get("generated_at"));
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (generatedAt == null || generatedAt.isAfter(now)) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "generated_at không hợp lệ hoặc nằm trong tương lai");
            }
            String stockFingerprint = metadata.get("stock_fingerprint");
            if (stockFingerprint == null || !stockFingerprint.matches("(?i)^[0-9a-f]{64}$")) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "Workbook thiếu stock_fingerprint hợp lệ");
            }
            Sheet sheet = workbook.getSheet("MOVEMENTS");
            if (sheet == null) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "Workbook thiếu sheet MOVEMENTS");
            }
            Map<String, Integer> headers = headers(sheet);
            List<ParsedRow> parsedRows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (blank(row, headers.values())) {
                    continue;
                }
                Map<String, Object> payload = values(row, headers);
                List<Map<String, String>> errors = new ArrayList<>();
                validateRow(payload, actor, tenantId, warehouseId, generatedAt, now, errors);
                parsedRows.add(new ParsedRow(index + 1, payload, errors));
            }
            if (parsedRows.isEmpty()) {
                parsedRows.add(new ParsedRow(2, Map.of(), List.of(error(
                        "MOVEMENT_ROWS_REQUIRED", "MOVEMENTS phải có ít nhất một dòng dữ liệu"))));
            }
            validateGroups(parsedRows);
            if (parsedRows.stream().map(row -> string(row.payload().get("movement_ref")))
                    .filter(value -> !value.isBlank()).distinct().count() > properties.getMaxMovementGroups()) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                        "Số movement_ref vượt quá giới hạn cho phép");
            }
            List<WmsImportRowInput> rows = parsedRows.stream()
                    .map(row -> new WmsImportRowInput("MOVEMENTS", row.rowNumber(),
                            string(row.payload().get("movement_ref")), row.payload(), row.errors()))
                    .toList();
            return new ParsedWorkbook(rows, generatedAt, stockFingerprint,
                    (int) rows.stream().map(WmsImportRowInput::groupKey).filter(value -> value != null && !value.isBlank()).distinct().count());
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Không thể đọc workbook movement");
        }
    }

    private void validateMetadata(Map<String, String> metadata, UUID tenantId, UUID warehouseId) {
        if (!SCHEMA_VERSION.equals(metadata.get("schema_version"))
                || !WORKBOOK_TYPE.equals(metadata.get("workbook_type"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "schema_version hoặc workbook_type không đúng");
        }
        if (!tenantId.toString().equalsIgnoreCase(metadata.get("tenant_id"))
                || !warehouseId.toString().equalsIgnoreCase(metadata.get("warehouse_id"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "Workbook không thuộc tenant hoặc warehouse hiện tại");
        }
        if (!BUSINESS_ZONE.getId().equals(metadata.get("timezone"))) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "Workbook phải dùng timezone Asia/Ho_Chi_Minh");
        }
    }

    private void validateRow(Map<String, Object> payload, User actor, UUID tenantId, UUID warehouseId,
                             LocalDateTime generatedAt, LocalDateTime now,
                             List<Map<String, String>> errors) {
        String reference = string(payload.get("movement_ref"));
        String type = upper(payload.get("type"));
        String skuCode = string(payload.get("sku_code"));
        String rackCode = string(payload.get("rack_code"));
        String binCode = string(payload.get("bin_code"));
        if (reference.isBlank()) addError(errors, "MOVEMENT_REF_REQUIRED", "movement_ref là bắt buộc");
        if (reference.length() > 120) addError(errors, "MOVEMENT_REF_TOO_LONG", "movement_ref không quá 120 ký tự");
        int sequence = integer(payload.get("sequence_no"), errors, "sequence_no");
        if (sequence <= 0) addError(errors, "SEQUENCE_POSITIVE_REQUIRED", "sequence_no phải lớn hơn 0");
        if (!Set.of("INBOUND", "OUTBOUND").contains(type)) {
            addError(errors, "MOVEMENT_TYPE_INVALID", "type phải là INBOUND hoặc OUTBOUND");
        }
        if (skuCode.isBlank()) addError(errors, "SKU_CODE_REQUIRED", "sku_code là bắt buộc");
        int quantity = integer(payload.get("quantity"), errors, "quantity");
        if (quantity <= 0) addError(errors, "QUANTITY_POSITIVE_REQUIRED", "quantity phải lớn hơn 0");
        LocalDateTime occurredAt = parseTimestamp(string(payload.get("occurred_at")));
        if (occurredAt == null) {
            addError(errors, "OCCURRED_AT_INVALID", "occurred_at phải là ISO-8601 date-time");
        } else if (occurredAt.isBefore(generatedAt) || occurredAt.isAfter(now)) {
            addError(errors, "OCCURRED_AT_OUT_OF_RANGE", "occurred_at phải nằm từ generated_at đến thời điểm hiện tại");
        }

        ProductSku sku = findSku(tenantId, skuCode);
        if (sku == null) addError(errors, "SKU_NOT_VISIBLE", "sku_code không tồn tại hoặc không còn hoạt động");

        boolean hasRack = !rackCode.isBlank();
        boolean hasBin = !binCode.isBlank();
        if ("INBOUND".equals(type)) {
            if (!hasRack || !hasBin) {
                addError(errors, "INBOUND_LOCATION_REQUIRED", "INBOUND phải có rack_code và bin_code");
            } else if (!hasPermission(actor, "INBOUND_CREATE")) {
                addError(errors, "INBOUND_PERMISSION_REQUIRED", "Tài khoản không có quyền tạo phiếu nhập");
            } else {
                validateLocation(warehouseId, rackCode, binCode, errors);
            }
        } else if ("OUTBOUND".equals(type)) {
            if (hasRack != hasBin) {
                addError(errors, "OUTBOUND_LOCATION_PAIR_REQUIRED", "OUTBOUND phải để trống cả hai location hoặc nhập đủ cả hai");
            }
            if (!hasPermission(actor, "OUTBOUND_CREATE")) {
                addError(errors, "OUTBOUND_PERMISSION_REQUIRED", "Tài khoản không có quyền tạo phiếu xuất");
            }
            if (hasRack && hasBin) validateLocation(warehouseId, rackCode, binCode, errors);
        }
    }

    private void validateGroups(List<ParsedRow> rows) {
        Map<String, List<ParsedRow>> groups = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            String reference = string(row.payload().get("movement_ref"));
            if (!reference.isBlank()) groups.computeIfAbsent(reference, ignored -> new ArrayList<>()).add(row);
        }
        Set<Integer> sequences = new HashSet<>();
        for (Map.Entry<String, List<ParsedRow>> entry : groups.entrySet()) {
            List<ParsedRow> group = entry.getValue();
            ParsedRow first = group.get(0);
            String type = upper(first.payload().get("type"));
            String occurredAt = string(first.payload().get("occurred_at"));
            int sequence = integer(first.payload().get("sequence_no"), first.errors(), "sequence_no");
            if (!sequences.add(sequence)) {
                addGroupError(group, "SEQUENCE_DUPLICATE", "sequence_no phải duy nhất giữa các movement_ref");
            }
            boolean manualOutbound = false;
            Set<String> skuLocations = new HashSet<>();
            for (ParsedRow row : group) {
                if (!type.equals(upper(row.payload().get("type")))
                        || !occurredAt.equals(string(row.payload().get("occurred_at")))
                        || sequence != integer(row.payload().get("sequence_no"), row.errors(), "sequence_no")) {
                    addError(row.errors(), "MOVEMENT_GROUP_INCONSISTENT",
                            "Các dòng cùng movement_ref phải có type, occurred_at và sequence_no giống nhau");
                }
                String rack = string(row.payload().get("rack_code"));
                String bin = string(row.payload().get("bin_code"));
                if ("OUTBOUND".equals(type) && (!rack.isBlank() || !bin.isBlank())) manualOutbound = true;
                String location = rack.toLowerCase(Locale.ROOT) + "|" + bin.toLowerCase(Locale.ROOT);
                String skuLocation = string(row.payload().get("sku_code")).toLowerCase(Locale.ROOT) + "|" + location;
                if (!skuLocations.add(skuLocation)) {
                    addError(row.errors(), "SKU_LOCATION_DUPLICATE",
                            "Một SKU không được lặp lại tại cùng một location trong cùng movement_ref");
                }
            }
            if ("OUTBOUND".equals(type)) {
                for (ParsedRow row : group) {
                    boolean rowManual = !string(row.payload().get("rack_code")).isBlank()
                            || !string(row.payload().get("bin_code")).isBlank();
                    if (rowManual != manualOutbound) {
                        addError(row.errors(), "OUTBOUND_MODE_MIXED",
                                "Một movement_ref OUTBOUND không được trộn auto FIFO và manual location");
                    }
                }
            }
        }
    }

    private void validateLocation(UUID warehouseId, String rackCode, String binCode,
                                  List<Map<String, String>> errors) {
        WarehouseLayout currentLayout = layoutRepository.findByWarehouseId(warehouseId).stream()
                .filter(this::activeLayout)
                .filter(candidate -> candidate.getTenant() != null)
                .max(Comparator.comparing(WarehouseLayout::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (currentLayout == null) {
            addError(errors, "LAYOUT_NOT_FOUND", "Warehouse chưa có tenant layout hoạt động");
            return;
        }
        WarehouseRack rack = rackRepository.findAllByLayoutId(currentLayout.getId()).stream()
                .filter(this::activeRack)
                .filter(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(rackCode))
                .findFirst().orElse(null);
        if (rack == null) {
            addError(errors, "RACK_NOT_VISIBLE", "rack_code không thuộc layout hiện tại của warehouse");
            return;
        }
        boolean binExists = binRepository.findAllByRackId(rack.getId()).stream()
                .filter(this::activeBin)
                .anyMatch(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(binCode));
        if (!binExists) addError(errors, "BIN_NOT_VISIBLE", "bin_code không thuộc rack_code hiện tại");
    }

    private WarehouseLayout activeTenantLayout(UUID warehouseId, UUID tenantId) {
        return layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(this::activeLayout)
                .orElseGet(() -> layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)
                        .filter(this::activeLayout)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND)));
    }

    private Warehouse activeWarehouse(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .filter(warehouse -> warehouse.isActive() && !warehouse.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    private void requireObservation(User actor, UUID tenantId, UUID warehouseId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (isStaff(actor)) accessService.requireActiveStaffAssignment(actor.getId(), tenantId, warehouseId);
    }

    private void requireWmsAccess(User actor, UUID tenantId, UUID warehouseId) {
        requireObservation(actor, tenantId, warehouseId);
        accessService.requireActiveSubscription(tenantId);
    }

    private boolean isStaff(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName()));
    }

    private boolean hasPermission(User user, String permission) {
        return user.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    private ProductSku findSku(UUID tenantId, String code) {
        if (code.isBlank()) return null;
        return skuRepository.findVisibleActiveByTenantAndSkuCodes(tenantId,
                        Set.of(code.toLowerCase(Locale.ROOT))).stream().findFirst().orElse(null);
    }

    private Map<String, Integer> headers(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                "MOVEMENTS header is missing");
        Map<String, Integer> result = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        DataFormatter formatter = new DataFormatter();
        for (int index = 0; index < header.getLastCellNum(); index++) {
            String value = formatter.formatCellValue(header.getCell(index)).trim();
            if (!value.isBlank() && !duplicates.add(value)) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                        "MOVEMENTS có header trùng: " + value);
            }
            if (MOVEMENT_HEADER_SET.contains(value)) result.put(value, index);
        }
        if (!result.keySet().equals(MOVEMENT_HEADER_SET)) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED,
                    "MOVEMENTS headers không khớp template");
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        MOVEMENT_HEADERS.forEach(name -> ordered.put(name, result.get(name)));
        return ordered;
    }

    private Map<String, Object> values(Row row, Map<String, Integer> headers) {
        DataFormatter formatter = new DataFormatter();
        Map<String, Object> values = new LinkedHashMap<>();
        headers.forEach((name, index) -> {
            String value = formatter.formatCellValue(row.getCell(index)).trim();
            if (value.length() > properties.getMaxTextCellLength()) {
                throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                        "Cell value quá dài tại MOVEMENTS!" + row.getRowNum());
            }
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
        for (Row row : sheet) {
            if (row.getLastCellNum() >= 2) {
                metadata.put(formatter.formatCellValue(row.getCell(0)).trim(),
                        formatter.formatCellValue(row.getCell(1)).trim());
            }
        }
        return metadata;
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID);
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Không thể đọc file upload");
        }
    }

    private LocalDateTime parseTimestamp(Object value) {
        String text = string(value);
        if (text.isBlank()) return null;
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(text);
                } catch (DateTimeParseException ignoredFinal) {
                    return null;
                }
            }
        }
    }

    private int integer(Object value, List<Map<String, String>> errors, String field) {
        String text = string(value);
        try {
            return new BigDecimal(text).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            addError(errors, field.toUpperCase(Locale.ROOT) + "_INVALID", field + " phải là số nguyên");
            return 0;
        }
    }

    private String upper(Object value) {
        return string(value).toUpperCase(Locale.ROOT);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void addGroupError(List<ParsedRow> rows, String code, String message) {
        rows.forEach(row -> addError(row.errors(), code, message));
    }

    private void addError(List<Map<String, String>> errors, String code, String message) {
        errors.add(error(code, message));
    }

    private Map<String, String> error(String code, String message) {
        return Map.of("code", code, "message", message);
    }

    private boolean activeLayout(WarehouseLayout layout) {
        return layout != null && layout.isActive() && !layout.isDeleted();
    }

    private boolean activeRack(WarehouseRack rack) {
        return rack != null && rack.isActive() && !rack.isDeleted();
    }

    private boolean activeBin(WarehouseBin bin) {
        return bin != null && bin.isActive() && !bin.isDeleted();
    }

    private record ParsedRow(int rowNumber, Map<String, Object> payload,
                             List<Map<String, String>> errors) {
    }

    private record ParsedWorkbook(List<WmsImportRowInput> rows, LocalDateTime generatedAt,
                                 String stockFingerprint, int groupCount) {
    }
}
