package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.StockLocationDto;
import fu.stockspace.stockspace_be.wms.stock.dto.StockSummaryResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.WarehouseStockOverviewResponse;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockBatchService {

    private final StockBatchRepository stockBatchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductSkuRepository productSkuRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final TenantWarehouseAccessService accessService;
    private final StaffWarehouseAssignmentRepository assignmentRepository;





    @Transactional(readOnly = true)
    public PagedResponse<StockBatchResponse> getStockByWarehouse(UUID tenantId, UUID warehouseId, Pageable pageable) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireActiveContract(tenantId, warehouseId);

        Page<StockBatch> page = stockBatchRepository.findByWarehouseIdAndTenantId(
                warehouseId, tenantId, pageable);

        return PagedResponse.fromPage(page, this::mapToResponse);
    }





    @Transactional(readOnly = true)
    public PagedResponse<StockBatchResponse> getStockByWarehouse(
            UUID tenantId, UUID warehouseId, UUID staffId, Pageable pageable) {
        requireActiveWarehouseAccess(tenantId, warehouseId, staffId);
        return getStockByWarehouse(tenantId, warehouseId, pageable);
    }






    @Transactional(readOnly = true)
    public PagedResponse<WarehouseStockOverviewResponse> getStockOverviewByWarehouse(
            UUID tenantId, UUID warehouseId, Pageable pageable) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireActiveContract(tenantId, warehouseId);

        Page<ProductSkuRepository.WarehouseStockOverviewProjection> page =
                productSkuRepository.findWarehouseStockOverview(tenantId, warehouseId, pageable);

        return PagedResponse.fromPage(page, row -> WarehouseStockOverviewResponse.builder()
                .skuId(row.getSkuId())
                .skuCode(row.getSkuCode())
                .skuName(row.getSkuName())
                .categoryId(row.getCategoryId())
                .categoryName(row.getCategoryName())
                .uomSymbol(row.getUomSymbol())
                .uomName(row.getUomName())
                .unitWeightKg(row.getUnitWeightKg())
                .unitVolumeM3(row.getUnitVolumeM3())
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .totalQuantity(row.getTotalQuantity())
                .totalWeightKg(row.getTotalWeightKg())
                .totalVolumeM3(row.getTotalVolumeM3())
                .build());
    }





    @Transactional(readOnly = true)
    public PagedResponse<WarehouseStockOverviewResponse> getStockOverviewByWarehouse(
            UUID tenantId, UUID warehouseId, UUID staffId, Pageable pageable) {
        requireActiveWarehouseAccess(tenantId, warehouseId, staffId);
        return getStockOverviewByWarehouse(tenantId, warehouseId, pageable);
    }





    @Transactional(readOnly = true)
    public WarehouseStockSummary getStockSummaryByWarehouse(UUID tenantId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireActiveContract(tenantId, warehouseId);

        StockBatchRepository.WarehouseStockSummaryProjection summary =
                stockBatchRepository.summarizeByWarehouseIdAndTenantId(warehouseId, tenantId);

        return new WarehouseStockSummary(
                warehouse.getId(),
                warehouse.getName(),
                valueOrZero(summary == null ? null : summary.getProductCount()),
                valueOrZero(summary == null ? null : summary.getBatchCount()),
                valueOrZero(summary == null ? null : summary.getTotalQuantity())
        );
    }




    @Transactional(readOnly = true)
    public StockSummaryResponse getStockSummaryBySku(UUID tenantId, UUID skuId) {
        return getStockSummaryBySku(tenantId, skuId, null);
    }





    @Transactional(readOnly = true)
    public StockSummaryResponse getStockSummaryBySku(UUID tenantId, UUID skuId, UUID staffId) {
        ProductSku sku = productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        UnitOfMeasure uom = sku.getUom();
        List<StockBatch> batches = staffId == null
                ? stockBatchRepository.findBySkuIdInActiveTenantWarehouses(skuId, tenantId)
                : stockBatchRepository.findBySkuIdInActiveAssignedTenantWarehouses(skuId, tenantId, staffId);
        int totalQuantity = batches.stream().mapToInt(StockBatch::getQuantity).sum();

        List<StockLocationDto> locations = batches.stream()
                .map(b -> StockLocationDto.builder()
                        .batchId(b.getId())
                        .warehouseId(b.getWarehouse() != null ? b.getWarehouse().getId() : null)
                        .warehouseName(b.getWarehouse() != null ? b.getWarehouse().getName() : null)
                        .rackName(b.getRack() != null ? b.getRack().getName() : null)
                        .binName(b.getBin() != null ? b.getBin().getName() : null)
                        .quantity(b.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return StockSummaryResponse.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .skuName(sku.getName())
                .uomSymbol(uom != null ? uom.getCode() : null)
                .uomName(uom != null ? uom.getName() : null)
                .totalQuantity(totalQuantity)
                .locations(locations)
                .build();
    }

    private void requireActiveWarehouseAccess(UUID tenantId, UUID warehouseId, UUID staffId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (staffId != null
                && !assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    public record WarehouseStockSummary(
            UUID warehouseId,
            String warehouseName,
            long productCount,
            long batchCount,
            long totalQuantity
    ) {
    }




    @Transactional
    public void adjustQuantity(UUID batchId, int delta) {
        StockBatch batch = stockBatchRepository.findByIdAndIsDeletedFalse(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        int newQty = batch.getQuantity() + delta;
        if (newQty < 0) {
            throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
        }
        batch.setQuantity(newQty);
        stockBatchRepository.save(batch);
        log.info("WMS Stock: Adjusted batch {} quantity by {} → new qty={}", batchId, delta, newQty);
    }




    @Transactional
    public StockBatch findOrCreateBatch(UUID skuId, UUID warehouseId, UUID rackId, UUID binId) {
        return stockBatchRepository
                .findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                        skuId, warehouseId, rackId, binId)
                .orElseGet(() -> {
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
                    WarehouseRack rack = rackId != null
                            ? rackRepository.findByIdAndIsDeletedFalse(rackId).orElse(null)
                            : null;
                    WarehouseBin bin = binId != null
                            ? binRepository.findByIdAndIsDeletedFalse(binId).orElse(null)
                            : null;

                    StockBatch newBatch = StockBatch.builder()
                            .skuId(skuId)
                            .warehouse(warehouse)
                            .rack(rack)
                            .bin(bin)
                            .quantity(0)
                            .arrivalDate(LocalDateTime.now())
                            .build();
                    return stockBatchRepository.save(newBatch);
                });
    }



    public StockBatchResponse mapToResponse(StockBatch b) {
        ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(b.getSkuId()).orElse(null);
        UnitOfMeasure uom = sku != null ? sku.getUom() : null;

        return StockBatchResponse.builder()
                .id(b.getId())
                .skuId(b.getSkuId())
                .skuCode(sku != null ? sku.getSkuCode() : null)
                .skuName(sku != null ? sku.getName() : null)
                .uomSymbol(uom != null ? uom.getCode() : null)
                .uomName(uom != null ? uom.getName() : null)
                .warehouseId(b.getWarehouse() != null ? b.getWarehouse().getId() : null)
                .warehouseName(b.getWarehouse() != null ? b.getWarehouse().getName() : null)
                .rackId(b.getRack() != null ? b.getRack().getId() : null)
                .rackName(b.getRack() != null ? b.getRack().getName() : null)
                .binId(b.getBin() != null ? b.getBin().getId() : null)
                .binName(b.getBin() != null ? b.getBin().getName() : null)
                .quantity(b.getQuantity())
                .arrivalDate(b.getArrivalDate())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }




    @Transactional(readOnly = true)
    public PagedResponse<StockBatchResponse> getAdminStockByWarehouse(UUID warehouseId, Pageable pageable) {
        Page<StockBatch> page = stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(warehouseId, pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }
}

