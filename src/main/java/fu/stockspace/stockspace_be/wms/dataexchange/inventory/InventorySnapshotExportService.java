package fu.stockspace.stockspace_be.wms.dataexchange.inventory;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.stock.repository.InventorySnapshotRow;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferReservationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventorySnapshotExportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WarehouseRepository warehouseRepository;
    private final TenantWarehouseAccessService accessService;
    private final StockBatchRepository stockBatchRepository;
    private final StockTransferReservationRepository reservationRepository;
    private final StockFingerprintService fingerprintService;
    private final DataExchangeProperties properties;

    @Transactional(readOnly = true)
    public byte[] export(UUID tenantId, UUID warehouseId, UUID staffId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (staffId != null) {
            accessService.requireActiveStaffAssignment(staffId, tenantId, warehouseId);
        }
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        List<InventorySnapshotRow> rows = stockBatchRepository.findInventorySnapshotRows(tenantId, warehouseId);
        if (rows.size() > properties.getMaxRows()) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                    "Inventory snapshot quá lớn để xuất thành một workbook");
        }
        Map<UUID, Long> reservedByBatch = rows.isEmpty() ? Map.of()
                : reservationRepository.sumActiveQuantityByBatchIds(
                                rows.stream().map(InventorySnapshotRow::batchId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                StockTransferReservationRepository.BatchReservationProjection::getBatchId,
                                value -> value.getReservedQuantity() == null ? 0L : value.getReservedQuantity()));
        ZonedDateTime generatedAt = ZonedDateTime.now(BUSINESS_ZONE);
        return render(tenantId, warehouse, warehouseId, rows, reservedByBatch,
                fingerprintService.fingerprint(warehouseId), generatedAt);
    }

    private byte[] render(UUID tenantId, Warehouse warehouse, UUID warehouseId,
                          List<InventorySnapshotRow> rows, Map<UUID, Long> reservedByBatch,
                          String fingerprint, ZonedDateTime generatedAt) {
        try (Workbook workbook = XlsxWorkbookWriter.newStreamingWorkbook()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", "1.0");
            metadata.put("workbook_type", "INVENTORY_SNAPSHOT");
            metadata.put("export_id", UUID.randomUUID());
            metadata.put("generated_at", generatedAt);
            metadata.put("tenant_id", tenantId);
            metadata.put("warehouse_id", warehouseId);
            metadata.put("warehouse_name", warehouse.getName());
            metadata.put("stock_fingerprint", fingerprint);
            metadata.put("timezone", BUSINESS_ZONE.getId());
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);
            writeReadme(workbook, generatedAt);
            writeByBatch(workbook, rows, reservedByBatch, generatedAt);
            writeSummary(workbook, rows, reservedByBatch, generatedAt);
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (IOException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Unable to render inventory snapshot workbook", ex);
        }
    }

    private void writeReadme(Workbook workbook, ZonedDateTime generatedAt) {
        Sheet sheet = workbook.createSheet("README");
        sheet.createRow(0).createCell(0).setCellValue("StockSpace Inventory Snapshot Workbook v1.0");
        sheet.createRow(1).createCell(0).setCellValue("Generated at: " + generatedAt);
        sheet.createRow(3).createCell(0).setCellValue("Read-only snapshot. Do not use this workbook as an import file.");
        sheet.createRow(4).createCell(0).setCellValue("Reserved quantity is held by active warehouse transfers; available = max(quantity - reserved, 0).");
        sheet.setColumnWidth(0, 35 * 256);
    }

    private void writeByBatch(Workbook workbook, List<InventorySnapshotRow> rows,
                              Map<UUID, Long> reservedByBatch, ZonedDateTime generatedAt) {
        Sheet sheet = workbook.createSheet("STOCK_BY_BATCH");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header,
                "batch_id", "sku_id", "sku_code", "sku_name", "category", "uom_code", "uom_name",
                "warehouse_id", "warehouse_name", "rack_id", "rack_code", "rack_name", "bin_id", "bin_code", "bin_name",
                "shelf_level", "batch_quantity", "reserved_quantity", "available_quantity", "arrival_date",
                "unit_weight_kg", "unit_volume_m3", "calculated_batch_weight_kg", "calculated_batch_volume_m3", "generated_at");
        int index = 1;
        for (InventorySnapshotRow item : rows) {
            long reserved = reservedByBatch.getOrDefault(item.batchId(), 0L);
            long available = Math.max(0L, item.quantity() - reserved);
            Row row = sheet.createRow(index++);
            Object[] values = {item.batchId(), item.skuId(), item.skuCode(), item.skuName(), item.categoryName(),
                    item.uomCode(), item.uomName(), item.warehouseId(), item.warehouseName(), item.rackId(), item.rackCode(),
                    item.rackName(), item.binId(), item.binCode(), item.binName(), item.shelfLevel(), item.quantity(), reserved,
                    available, item.arrivalDate(), item.unitWeightKg(), item.unitVolumeM3(), multiply(item.unitWeightKg(), item.quantity()),
                    multiply(item.unitVolumeM3(), item.quantity()), generatedAt};
            for (int column = 0; column < values.length; column++) write(row, column, values[column], readOnly);
        }
        finishSheet(sheet, 26);
    }

    private void writeSummary(Workbook workbook, List<InventorySnapshotRow> rows,
                              Map<UUID, Long> reservedByBatch, ZonedDateTime generatedAt) {
        Map<UUID, Summary> summaries = new LinkedHashMap<>();
        for (InventorySnapshotRow item : rows) {
            Summary summary = summaries.computeIfAbsent(item.skuId(), ignored -> new Summary(item));
            long reserved = reservedByBatch.getOrDefault(item.batchId(), 0L);
            summary.quantity += item.quantity();
            summary.reserved += reserved;
            summary.weight = summary.weight.add(multiply(item.unitWeightKg(), item.quantity()));
            summary.volume = summary.volume.add(multiply(item.unitVolumeM3(), item.quantity()));
            summary.locations.put((item.rackId() + ":" + item.binId()), Boolean.TRUE);
        }
        Sheet sheet = workbook.createSheet("STOCK_SUMMARY");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header,
                "sku_id", "sku_code", "sku_name", "category", "uom_code", "uom_name", "total_quantity",
                "reserved_quantity", "available_quantity", "total_weight_kg", "total_volume_m3", "location_count", "generated_at");
        int index = 1;
        for (Summary item : summaries.values()) {
            Row row = sheet.createRow(index++);
            Object[] values = {item.skuId, item.skuCode, item.skuName, item.categoryName, item.uomCode, item.uomName,
                    item.quantity, item.reserved, Math.max(0L, item.quantity - item.reserved), item.weight, item.volume,
                    item.locations.size(), generatedAt};
            for (int column = 0; column < values.length; column++) write(row, column, values[column], readOnly);
        }
        finishSheet(sheet, 13);
    }

    private void finishSheet(Sheet sheet, int columns) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, columns - 1));
        sheet.protectSheet("wms-data");
    }

    private void write(Row row, int column, Object value, CellStyle style) {
        var cell = row.createCell(column);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(XlsxWorkbookWriter.safeText(value));
        cell.setCellStyle(style);
    }

    private BigDecimal multiply(BigDecimal value, int quantity) {
        return value == null ? BigDecimal.ZERO : value.multiply(BigDecimal.valueOf(quantity));
    }

    private static class Summary {
        private final UUID skuId;
        private final String skuCode;
        private final String skuName;
        private final String categoryName;
        private final String uomCode;
        private final String uomName;
        private long quantity;
        private long reserved;
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal volume = BigDecimal.ZERO;
        private final Map<String, Boolean> locations = new LinkedHashMap<>();

        private Summary(InventorySnapshotRow item) {
            skuId = item.skuId(); skuCode = item.skuCode(); skuName = item.skuName();
            categoryName = item.categoryName(); uomCode = item.uomCode(); uomName = item.uomName();
        }
    }
}
