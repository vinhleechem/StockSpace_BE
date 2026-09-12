package fu.stockspace.stockspace_be.wms.dataexchange.audit;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
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
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exports only the current blind-count round of an accessible audit. */
@Service
@RequiredArgsConstructor
public class AuditCountSheetExportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InventoryAuditRepository auditRepository;
    private final InventoryAuditItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final ProductSkuRepository skuRepository;
    private final DataExchangeProperties properties;

    @Transactional(readOnly = true)
    public byte[] export(UUID actorId, UUID auditId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        InventoryAudit audit = auditRepository.findById(auditId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
        UUID tenantId = resolveTenantId(actor);
        assertAuditAccess(actor, tenantId, audit);
        if (audit.getStatus() != AuditStatus.IN_PROGRESS && audit.getStatus() != AuditStatus.REOPENED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS,
                    "Chỉ có thể xuất count sheet ở trạng thái IN_PROGRESS hoặc REOPENED");
        }

        List<InventoryAuditItem> items = itemRepository
                .findByAuditIdAndCountRoundOrderById(auditId, audit.getCountRound());
        if (items.size() > properties.getMaxRows()) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                    "Audit count sheet vượt quá giới hạn workbook");
        }
        WarehouseLayout layout = currentLayout(audit, tenantId);
        List<WarehouseRack> racks = rackRepository.findAllByLayoutId(layout.getId()).stream()
                .filter(this::activeRack)
                .filter(rack -> inScope(audit, rack, null))
                .sorted(Comparator.comparing(WarehouseRack::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<WarehouseBin> bins = racks.stream()
                .flatMap(rack -> binRepository.findAllByRackId(rack.getId()).stream())
                .filter(this::activeBin)
                .filter(bin -> inScope(audit, bin.getRack(), bin))
                .sorted(Comparator.comparing(WarehouseBin::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<ProductSku> skus = skuRepository.findAllActiveByTenantOrSystem(
                        tenantId, PageRequest.of(0, properties.getMaxRows()))
                .getContent().stream().filter(ProductSku::isActive)
                .sorted(Comparator.comparing(ProductSku::getSkuCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        ZonedDateTime generatedAt = ZonedDateTime.now(BUSINESS_ZONE);
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", "1.0");
            metadata.put("workbook_type", "AUDIT_COUNT");
            metadata.put("export_id", UUID.randomUUID());
            metadata.put("generated_at", generatedAt);
            metadata.put("tenant_id", tenantId);
            metadata.put("audit_id", auditId);
            metadata.put("warehouse_id", audit.getWarehouse().getId());
            metadata.put("count_round", audit.getCountRound());
            metadata.put("audit_version", audit.getVersion());
            metadata.put("audit_status", audit.getStatus());
            metadata.put("scope_type", audit.getScopeType());
            metadata.put("scope_rack_id", audit.getScopeRack() == null ? null : audit.getScopeRack().getId());
            metadata.put("scope_bin_id", audit.getScopeBin() == null ? null : audit.getScopeBin().getId());
            metadata.put("layout_id", layout.getId());
            metadata.put("timezone", BUSINESS_ZONE.getId());
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);
            writeReadme(workbook, generatedAt, audit);
            writeCountItems(workbook, items);
            writeUnexpectedItems(workbook);
            writeSkuLookup(workbook, skus);
            writeLocationLookup(workbook, racks, bins);
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (IOException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Unable to render audit count workbook", ex);
        }
    }

    private void writeReadme(Workbook workbook, ZonedDateTime generatedAt, InventoryAudit audit) {
        Sheet sheet = workbook.createSheet("README");
        sheet.createRow(0).createCell(0).setCellValue("StockSpace Audit Count Workbook v1.0");
        sheet.createRow(1).createCell(0).setCellValue("Generated at: " + generatedAt);
        sheet.createRow(2).createCell(0).setCellValue("Audit status: " + audit.getStatus()
                + ", count round: " + audit.getCountRound());
        sheet.createRow(4).createCell(0).setCellValue("COUNT_ITEMS contains only the current count round. Expected quantity is intentionally hidden for blind counting.");
        sheet.createRow(5).createCell(0).setCellValue("Edit actual_quantity, note and variance_reason only. Do not change protected identifiers or lookup sheets.");
        sheet.createRow(6).createCell(0).setCellValue("UNEXPECTED_ITEMS is optional. Every SKU + rack + bin tuple may appear only once and must be inside the audit scope.");
        sheet.setColumnWidth(0, 125 * 256);
    }

    private void writeCountItems(Workbook workbook, List<InventoryAuditItem> items) {
        Sheet sheet = workbook.createSheet("COUNT_ITEMS");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        CellStyle editable = XlsxWorkbookWriter.editableStyle(workbook);
        List<String> headers = List.of("audit_item_id", "sku_code", "sku_name", "uom_code",
                "rack_code", "rack_name", "bin_code", "bin_name", "shelf_level",
                "actual_quantity", "note", "variance_reason");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        int rowIndex = 1;
        for (InventoryAuditItem item : items) {
            ProductSku sku = skuFor(item);
            WarehouseRack rack = item.getBatch() != null ? item.getBatch().getRack() : item.getRack();
            WarehouseBin bin = item.getBatch() != null ? item.getBatch().getBin() : item.getBin();
            Object[] values = {item.getId(), sku == null ? null : sku.getSkuCode(), sku == null ? null : sku.getName(),
                    sku == null || sku.getUom() == null ? null : sku.getUom().getCode(),
                    rack == null ? null : rack.getCode(), rack == null ? null : rack.getName(),
                    bin == null ? null : bin.getCode(), bin == null ? null : bin.getName(),
                    bin == null ? null : bin.getShelfLevel(), item.getActualQuantity(), item.getNote(), item.getVarianceReason()};
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < values.length; column++) {
                write(row, column, values[column], column <= 8 ? readOnly : editable);
            }
        }
        finishSheet(sheet, headers.size());
    }

    private void writeUnexpectedItems(Workbook workbook) {
        Sheet sheet = workbook.createSheet("UNEXPECTED_ITEMS");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle editable = XlsxWorkbookWriter.editableStyle(workbook);
        List<String> headers = List.of("sku_code", "rack_code", "bin_code", "actual_quantity", "note");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        Row entry = sheet.createRow(1);
        for (int column = 0; column < headers.size(); column++) entry.createCell(column).setCellStyle(editable);
        finishSheet(sheet, headers.size());
    }

    private void writeSkuLookup(Workbook workbook, List<ProductSku> skus) {
        Sheet sheet = workbook.createSheet("SKU_LOOKUP");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        List<String> headers = List.of("sku_code", "sku_name", "uom_code", "category_name");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        int rowIndex = 1;
        for (ProductSku sku : skus) {
            writeValues(sheet.createRow(rowIndex++), new Object[]{sku.getSkuCode(), sku.getName(),
                    sku.getUom() == null ? null : sku.getUom().getCode(),
                    sku.getCategory() == null ? null : sku.getCategory().getName()}, readOnly);
        }
        finishSheet(sheet, headers.size());
    }

    private void writeLocationLookup(Workbook workbook, List<WarehouseRack> racks,
                                     List<WarehouseBin> bins) {
        Sheet sheet = workbook.createSheet("LOCATION_LOOKUP");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        List<String> headers = List.of("rack_code", "rack_name", "bin_code", "bin_name", "shelf_level");
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, headers.toArray(String[]::new));
        Map<UUID, WarehouseRack> racksById = racks.stream()
                .collect(java.util.stream.Collectors.toMap(WarehouseRack::getId, rack -> rack));
        int rowIndex = 1;
        for (WarehouseBin bin : bins) {
            WarehouseRack rack = racksById.get(bin.getRack().getId());
            writeValues(sheet.createRow(rowIndex++), new Object[]{rack.getCode(), rack.getName(), bin.getCode(),
                    bin.getName(), bin.getShelfLevel()}, readOnly);
        }
        finishSheet(sheet, headers.size());
    }

    private void writeValues(Row row, Object[] values, CellStyle style) {
        for (int column = 0; column < values.length; column++) write(row, column, values[column], style);
    }

    private void write(Row row, int column, Object value, CellStyle style) {
        var cell = row.createCell(column);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(XlsxWorkbookWriter.safeText(value));
        cell.setCellStyle(style);
    }

    private void finishSheet(Sheet sheet, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, sheet.getLastRowNum()), 0, columns - 1));
        sheet.protectSheet("wms-data");
        for (int column = 0; column < columns; column++) sheet.autoSizeColumn(column);
    }

    private ProductSku skuFor(InventoryAuditItem item) {
        UUID skuId = item.getBatch() != null ? item.getBatch().getSkuId() : item.getSkuId();
        return skuId == null ? null : skuRepository.findByIdAndIsDeletedFalse(skuId).orElse(null);
    }

    private WarehouseLayout currentLayout(InventoryAudit audit, UUID tenantId) {
        return layoutRepository.findByWarehouseIdAndTenantId(audit.getWarehouse().getId(), tenantId)
                .filter(layout -> layout.isActive() && !layout.isDeleted())
                .orElseGet(() -> layoutRepository.findByWarehouseIdAndIsDefaultTrue(audit.getWarehouse().getId())
                        .filter(layout -> layout.isActive() && !layout.isDeleted())
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND)));
    }

    private void assertAuditAccess(User actor, UUID tenantId, InventoryAudit audit) {
        UUID auditTenantId = audit.getTenant() == null ? audit.getRequestedBy().getId() : audit.getTenant().getId();
        if (!tenantId.equals(auditTenantId)) throw new ForbiddenException(ErrorCode.FORBIDDEN);
        accessService.requireActiveContract(tenantId, audit.getWarehouse().getId());
        if (isStaff(actor)) {
            accessService.requireActiveStaffAssignment(actor.getId(), tenantId, audit.getWarehouse().getId());
            if (audit.getAssignedTo() == null || !actor.getId().equals(audit.getAssignedTo().getId())) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private UUID resolveTenantId(User actor) {
        if (!isStaff(actor)) return actor.getId();
        return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(actor.getId())
                .map(member -> member.getTenant().getId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
    }

    private boolean isStaff(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName()));
    }

    private boolean inScope(InventoryAudit audit, WarehouseRack rack, WarehouseBin bin) {
        if (audit.getScopeType() == null || audit.getScopeType() == AuditScopeType.WAREHOUSE) return true;
        if (audit.getScopeType() == AuditScopeType.RACK) {
            return audit.getScopeRack() != null && rack != null
                    && audit.getScopeRack().getId().equals(rack.getId());
        }
        return audit.getScopeBin() != null && bin != null
                && audit.getScopeBin().getId().equals(bin.getId());
    }

    private boolean activeRack(WarehouseRack rack) {
        return rack != null && rack.isActive() && !rack.isDeleted();
    }

    private boolean activeBin(WarehouseBin bin) {
        return bin != null && bin.isActive() && !bin.isDeleted();
    }
}
