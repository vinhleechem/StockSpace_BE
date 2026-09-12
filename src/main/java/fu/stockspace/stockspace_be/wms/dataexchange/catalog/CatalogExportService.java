package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogExportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ProductCategoryRepository categoryRepository;
    private final ProductSkuRepository skuRepository;
    private final UnitOfMeasureRepository uomRepository;
    private final DataExchangeProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public byte[] export(UUID tenantId) {
        List<ProductCategory> categories = categoryRepository.findAllActiveByTenantOrSystem(tenantId).stream()
                .sorted(Comparator.comparing(ProductCategory::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ProductCategory::getId))
                .toList();
        Page<ProductSku> skuPage = skuRepository.findAllActiveByTenantOrSystem(tenantId,
                PageRequest.of(0, properties.getMaxRows(), Sort.by("name").ascending().and(Sort.by("skuCode").ascending())));
        Page<UnitOfMeasure> uomPage = uomRepository.findAllActiveByTenantOrSystem(tenantId,
                PageRequest.of(0, properties.getMaxRows(), Sort.by("code").ascending()));
        if (categories.size() > properties.getMaxRows()
                || skuPage.getTotalElements() > properties.getMaxRows()
                || uomPage.getTotalElements() > properties.getMaxRows()) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED,
                    "Catalog quá lớn để xuất thành một workbook");
        }
        return render(tenantId, categories, skuPage.getContent(), uomPage.getContent());
    }

    private byte[] render(UUID tenantId, List<ProductCategory> categories,
                          List<ProductSku> skus, List<UnitOfMeasure> uoms) {
        UUID exportId = UUID.randomUUID();
        ZonedDateTime generatedAt = ZonedDateTime.now(BUSINESS_ZONE);
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", "1.0");
            metadata.put("workbook_type", "SKU_CATALOG");
            metadata.put("export_id", exportId);
            metadata.put("generated_at", generatedAt);
            metadata.put("tenant_id", tenantId);
            metadata.put("timezone", BUSINESS_ZONE.getId());
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);

            writeReadme(workbook, generatedAt);
            writeCategories(workbook, categories);
            writeSkus(workbook, skus);
            writeUomLookup(workbook, uoms);
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (IOException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Unable to render catalog workbook", ex);
        }
    }

    private void writeReadme(Workbook workbook, ZonedDateTime generatedAt) {
        Sheet sheet = workbook.createSheet("README");
        sheet.createRow(0).createCell(0).setCellValue("StockSpace SKU Catalog Workbook v1.0");
        sheet.createRow(1).createCell(0).setCellValue("Generated at: " + generatedAt);
        sheet.createRow(3).createCell(0).setCellValue("CATEGORIES: existing rows are read-only; add new rows only through the import template.");
        sheet.createRow(4).createCell(0).setCellValue("SKUS: tenant rows may be edited for UPDATE; sku_id, source_updated_at and sku_code are protected.");
        sheet.createRow(5).createCell(0).setCellValue("SYSTEM rows are read-only defaults and cannot be updated through tenant import.");
        sheet.createRow(6).createCell(0).setCellValue("UOM_LOOKUP is lookup-only and is not imported.");
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 30 * 256);
    }

    private void writeCategories(Workbook workbook, List<ProductCategory> categories) {
        Sheet sheet = workbook.createSheet("CATEGORIES");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header,
                "action", "category_key", "category_id", "source", "name", "default_attributes_json");
        int rowIndex = 1;
        for (ProductCategory category : categories) {
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, "SKIP", readOnly);
            write(row, 1, category.getId(), readOnly);
            write(row, 2, category.getId(), readOnly);
            write(row, 3, source(category.getTenant()), readOnly);
            write(row, 4, category.getName(), readOnly);
            write(row, 5, json(category.getDefaultAttributes()), readOnly);
        }
        finishSheet(sheet, 6);
    }

    private void writeSkus(Workbook workbook, List<ProductSku> skus) {
        Sheet sheet = workbook.createSheet("SKUS");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        CellStyle editable = XlsxWorkbookWriter.editableStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header,
                "action", "sku_id", "source_updated_at", "source", "sku_code", "name",
                "category_id", "category_key", "uom_code", "unit_weight_kg", "unit_volume_m3", "specifications_json");
        int rowIndex = 1;
        for (ProductSku sku : skus) {
            Row row = sheet.createRow(rowIndex++);
            boolean system = sku.getTenant() == null;
            CellStyle valueStyle = system ? readOnly : editable;
            write(row, 0, "SKIP", valueStyle);
            write(row, 1, sku.getId(), readOnly);
            write(row, 2, sku.getUpdatedAt(), readOnly);
            write(row, 3, source(sku.getTenant()), readOnly);
            write(row, 4, sku.getSkuCode(), readOnly);
            write(row, 5, sku.getName(), valueStyle);
            write(row, 6, sku.getCategory() == null ? null : sku.getCategory().getId(), valueStyle);
            write(row, 7, null, valueStyle);
            write(row, 8, sku.getUom() == null ? null : sku.getUom().getCode(), valueStyle);
            write(row, 9, sku.getUnitWeightKg(), valueStyle);
            write(row, 10, sku.getUnitVolumeM3(), valueStyle);
            write(row, 11, json(sku.getSpecifications()), valueStyle);
        }
        finishSheet(sheet, 13);
    }

    private void writeUomLookup(Workbook workbook, List<UnitOfMeasure> uoms) {
        Sheet sheet = workbook.createSheet("UOM_LOOKUP");
        CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
        CellStyle readOnly = XlsxWorkbookWriter.readOnlyStyle(workbook);
        XlsxWorkbookWriter.writeHeaders(sheet.createRow(0), header, "code", "name", "source", "description");
        int rowIndex = 1;
        for (UnitOfMeasure uom : uoms) {
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, uom.getCode(), readOnly);
            write(row, 1, uom.getName(), readOnly);
            write(row, 2, source(uom.getTenant()), readOnly);
            write(row, 3, uom.getDescription(), readOnly);
        }
        finishSheet(sheet, 4);
    }

    private void finishSheet(Sheet sheet, int columnCount) {
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, columnCount - 1));
        sheet.protectSheet("wms-data");
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void write(Row row, int column, Object value, CellStyle style) {
        var cell = row.createCell(column);
        if (value instanceof Number number) {
            cell.setCellValue(number instanceof BigDecimal decimal ? decimal.doubleValue() : number.doubleValue());
        } else {
            cell.setCellValue(XlsxWorkbookWriter.safeText(value));
        }
        cell.setCellStyle(style);
    }

    private String source(Object tenant) {
        return tenant == null ? "SYSTEM" : "TENANT";
    }

    private String json(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Catalog JSON field cannot be serialized", ex);
        }
    }
}
