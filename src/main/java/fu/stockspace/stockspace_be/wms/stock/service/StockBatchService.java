package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.StockLocationDto;
import fu.stockspace.stockspace_be.wms.stock.dto.StockSummaryResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.WarehouseStockOverviewResponse;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservationStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockBatchService {

    private final StockBatchRepository stockBatchRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductSkuRepository productSkuRepository;
    private final TenantWarehouseAccessService accessService;
    private final InventoryAuditLockService inventoryAuditLockService;
    private final StockTransferReservationRepository transferReservationRepository;
    private final InventoryAuditRepository inventoryAuditRepository;
    private final Clock businessClock;

    private static final Set<AuditStatus> BLIND_COUNT_STATUSES = Set.of(
            AuditStatus.IN_PROGRESS, AuditStatus.REOPENED, AuditStatus.RECOUNT_REQUIRED);





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
        List<InventoryAudit> audits = activeBlindCountAudits(tenantId, warehouseId, staffId);
        Page<StockBatch> page = stockBatchRepository.findByWarehouseIdAndTenantId(
                warehouseId, tenantId, pageable);
        return PagedResponse.fromPage(page, batch -> mapToResponse(batch, audits));
    }






    @Transactional(readOnly = true)
    public PagedResponse<WarehouseStockOverviewResponse> getStockOverviewByWarehouse(
            UUID tenantId, UUID warehouseId, Pageable pageable) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireActiveContract(tenantId, warehouseId);

        Page<ProductSkuRepository.WarehouseStockOverviewProjection> page =
                productSkuRepository.findWarehouseStockOverview(tenantId, warehouseId, pageable);

        return PagedResponse.fromPage(page, row -> {
            long reserved = transferReservationRepository == null ? 0L
                    : transferReservationRepository.sumActiveQuantityBySkuAndWarehouse(
                    row.getSkuId(), warehouseId);
            return WarehouseStockOverviewResponse.builder()
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
                .reservedQuantity(reserved)
                .availableQuantity(Math.max(0L, row.getTotalQuantity() - reserved))
                .totalWeightKg(row.getTotalWeightKg())
                .totalVolumeM3(row.getTotalVolumeM3())
                .build();
        });
    }





    @Transactional(readOnly = true)
    public PagedResponse<WarehouseStockOverviewResponse> getStockOverviewByWarehouse(
            UUID tenantId, UUID warehouseId, UUID staffId, Pageable pageable) {
        requireActiveWarehouseAccess(tenantId, warehouseId, staffId);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        List<InventoryAudit> audits = activeBlindCountAudits(tenantId, warehouseId, staffId);
        List<StockBatch> warehouseBatches = audits.isEmpty() ? List.of()
                : stockBatchRepository.findAllByWarehouseIdAndTenantId(warehouseId, tenantId);
        if (warehouseBatches == null) warehouseBatches = List.of();
        Set<UUID> maskedSkuIds = audits.isEmpty() ? Set.of() : warehouseBatches.stream()
                .filter(batch -> isInAnyAuditScope(batch, audits))
                .map(StockBatch::getSkuId)
                .collect(Collectors.toSet());
        Page<ProductSkuRepository.WarehouseStockOverviewProjection> page =
                productSkuRepository.findWarehouseStockOverview(tenantId, warehouseId, pageable);
        return PagedResponse.fromPage(page, row -> {
            boolean masked = maskedSkuIds.contains(row.getSkuId());
            long reserved = transferReservationRepository == null ? 0L
                    : transferReservationRepository.sumActiveQuantityBySkuAndWarehouse(row.getSkuId(), warehouseId);
            return WarehouseStockOverviewResponse.builder()
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
                    .totalQuantity(masked ? 0L : row.getTotalQuantity())
                    .reservedQuantity(masked ? 0L : reserved)
                    .availableQuantity(masked ? 0L : Math.max(0L, row.getTotalQuantity() - reserved))
                    .totalWeightKg(masked ? null : row.getTotalWeightKg())
                    .totalVolumeM3(masked ? null : row.getTotalVolumeM3())
                    .quantityMasked(masked)
                    .build();
        });
    }





    @Transactional(readOnly = true)
    public WarehouseStockSummary getStockSummaryByWarehouse(UUID tenantId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireActiveContract(tenantId, warehouseId);

        StockBatchRepository.WarehouseStockSummaryProjection summary =
                stockBatchRepository.summarizeByWarehouseIdAndTenantId(warehouseId, tenantId);

        long totalQuantity = valueOrZero(summary == null ? null : summary.getTotalQuantity());
        List<StockBatch> warehouseBatches = transferReservationRepository == null
                ? List.of() : stockBatchRepository.findAllByWarehouseIdAndTenantId(warehouseId, tenantId);
        if (warehouseBatches == null) {
            warehouseBatches = List.of();
        }
        long reservedQuantity = transferReservationRepository == null ? 0L
                : warehouseBatches.stream()
                .mapToLong(batch -> transferReservationRepository.sumQuantityByBatchAndStatusExcludingTransfer(
                        batch.getId(), StockTransferReservationStatus.ACTIVE, null))
                .sum();

        return new WarehouseStockSummary(
                warehouse.getId(),
                warehouse.getName(),
                valueOrZero(summary == null ? null : summary.getProductCount()),
                valueOrZero(summary == null ? null : summary.getBatchCount()),
                totalQuantity,
                reservedQuantity,
                Math.max(0L, totalQuantity - reservedQuantity)
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
                ? stockBatchRepository.findBySkuIdInActiveTenantWarehouses(
                skuId, tenantId, LocalDate.now(businessClock))
                : stockBatchRepository.findBySkuIdInActiveAssignedTenantWarehouses(
                skuId, tenantId, staffId, LocalDate.now(businessClock));
        List<InventoryAudit> audits = activeBlindCountAudits(tenantId, null, staffId);
        boolean quantityMasked = batches.stream().anyMatch(batch -> isInAnyAuditScope(batch, audits));
        int totalQuantity = batches.stream().mapToInt(StockBatch::getQuantity).sum();
        int reservedQuantity = transferReservationRepository == null ? 0 : batches.stream()
                .mapToInt(batch -> (int) Math.min(Integer.MAX_VALUE,
                        transferReservationRepository.sumQuantityByBatchAndStatusExcludingTransfer(
                                batch.getId(), StockTransferReservationStatus.ACTIVE, null)))
                .sum();

        List<StockLocationDto> locations = batches.stream()
                .map(b -> {
                    boolean masked = isInAnyAuditScope(b, audits);
                    int reservedForBatch = transferReservationRepository == null ? 0
                            : (int) Math.min(Integer.MAX_VALUE,
                            transferReservationRepository.sumQuantityByBatchAndStatusExcludingTransfer(
                                    b.getId(), StockTransferReservationStatus.ACTIVE, null));
                    return StockLocationDto.builder()
                        .batchId(b.getId())
                        .warehouseId(b.getWarehouse() != null ? b.getWarehouse().getId() : null)
                        .warehouseName(b.getWarehouse() != null ? b.getWarehouse().getName() : null)
                        .rackName(b.getRack() != null ? b.getRack().getName() : null)
                        .binName(b.getBin() != null ? b.getBin().getName() : null)
                        .quantity(masked ? 0 : b.getQuantity())
                        .reservedQuantity(masked ? 0 : reservedForBatch)
                        .availableQuantity(masked ? 0 : Math.max(0, b.getQuantity() - reservedForBatch))
                        .quantityMasked(masked)
                        .build();
                })
                .collect(Collectors.toList());

        return StockSummaryResponse.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .skuName(sku.getName())
                .uomSymbol(uom != null ? uom.getCode() : null)
                .uomName(uom != null ? uom.getName() : null)
                .totalQuantity(quantityMasked ? 0 : totalQuantity)
                .reservedQuantity(quantityMasked ? 0 : reservedQuantity)
                .availableQuantity(quantityMasked ? 0 : Math.max(0, totalQuantity - reservedQuantity))
                .quantityMasked(quantityMasked)
                .locations(locations)
                .build();
    }

    private void requireActiveWarehouseAccess(UUID tenantId, UUID warehouseId, UUID staffId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (staffId != null) {
            accessService.requireActiveStaffAssignment(staffId, tenantId, warehouseId);
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
            long totalQuantity,
            long reservedQuantity,
            long availableQuantity
    ) {
        /** Backward-compatible constructor for existing tool/test callers. */
        public WarehouseStockSummary(UUID warehouseId, String warehouseName,
                                     long productCount, long batchCount, long totalQuantity) {
            this(warehouseId, warehouseName, productCount, batchCount,
                    totalQuantity, 0L, totalQuantity);
        }
    }




    @Transactional
    public void adjustQuantity(UUID batchId, int delta) {
        StockBatch batch = stockBatchRepository.findByIdAndIsDeletedFalse(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        if (inventoryAuditLockService != null && batch.getWarehouse() != null) {
            inventoryAuditLockService.assertMovementAllowed(batch.getWarehouse().getId());
            // Re-read after the warehouse lock is acquired so two direct stock
            // mutations cannot calculate from the same stale quantity.
            batch = stockBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        }
        int newQty = batch.getQuantity() + delta;
        if (newQty < 0) {
            throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
        }
        if (delta < 0 && transferReservationRepository != null) {
            long reserved = transferReservationRepository.sumQuantityByBatchAndStatusExcludingTransfer(
                    batch.getId(), StockTransferReservationStatus.ACTIVE, null);
            if ((long) batch.getQuantity() - reserved < -((long) delta)) {
                throw new ResourceConflictException(
                        ErrorCode.STOCK_TRANSFER_RESERVATION_CONFLICT,
                        "Không thể giảm tồn kho đã được reservation cho transfer khác");
            }
        }
        batch.setQuantity(newQty);
        stockBatchRepository.save(batch);
        log.info("WMS Stock: Adjusted batch {} quantity by {} → new qty={}", batchId, delta, newQty);
    }




    public StockBatchResponse mapToResponse(StockBatch b) {
        return mapToResponse(b, List.of());
    }

    private StockBatchResponse mapToResponse(StockBatch b, List<InventoryAudit> audits) {
        ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(b.getSkuId()).orElse(null);
        UnitOfMeasure uom = sku != null ? sku.getUom() : null;
        int reservedQuantity = transferReservationRepository == null || b.getId() == null ? 0
                : (int) Math.min(Integer.MAX_VALUE,
                transferReservationRepository.sumQuantityByBatchAndStatusExcludingTransfer(
                        b.getId(), StockTransferReservationStatus.ACTIVE, null));
        boolean masked = isInAnyAuditScope(b, audits);

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
                .quantity(masked ? 0 : b.getQuantity())
                .reservedQuantity(masked ? 0 : reservedQuantity)
                .availableQuantity(masked ? 0 : Math.max(0, b.getQuantity() - reservedQuantity))
                .quantityMasked(masked)
                .arrivalDate(b.getArrivalDate())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private List<InventoryAudit> activeBlindCountAudits(UUID tenantId, UUID warehouseId, UUID staffId) {
        if (staffId == null || tenantId == null || inventoryAuditRepository == null) {
            return List.of();
        }
        List<InventoryAudit> audits = inventoryAuditRepository.findActiveBlindCountAudits(
                tenantId, staffId, BLIND_COUNT_STATUSES);
        if (audits == null) return List.of();
        return audits
                .stream()
                .filter(audit -> warehouseId == null || audit.getWarehouse() != null
                        && warehouseId.equals(audit.getWarehouse().getId()))
                .toList();
    }

    private boolean isInAnyAuditScope(StockBatch batch, List<InventoryAudit> audits) {
        if (batch == null || audits == null || audits.isEmpty()) return false;
        return audits.stream().anyMatch(audit -> {
            AuditScopeType scope = audit.getScopeType() == null ? AuditScopeType.WAREHOUSE : audit.getScopeType();
            if (scope == AuditScopeType.WAREHOUSE) return true;
            if (scope == AuditScopeType.RACK) {
                return audit.getScopeRack() != null && batch.getRack() != null
                        && audit.getScopeRack().getId().equals(batch.getRack().getId());
            }
            return audit.getScopeBin() != null && batch.getBin() != null
                    && audit.getScopeBin().getId().equals(batch.getBin().getId());
        });
    }




    @Transactional(readOnly = true)
    public PagedResponse<StockBatchResponse> getAdminStockByWarehouse(UUID warehouseId, Pageable pageable) {
        Page<StockBatch> page = stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(warehouseId, pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }
}

