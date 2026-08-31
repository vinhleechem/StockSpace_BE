package fu.stockspace.stockspace_be.wms.picking;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickLineResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickStopResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingItemResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only outbound picking preview. It plans against the current stock
 * snapshot and never creates receipts, reservations or stock mutations.
 */
@Service
@RequiredArgsConstructor
public class OutboundPickingSuggestionService {

    public static final String STRATEGY = "FIFO_SERPENTINE_XY_V1";

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final ProductSkuRepository productSkuRepository;
    private final StockBatchRepository stockBatchRepository;
    private final TenantWarehouseAccessService accessService;
    private final FifoAllocationPlanner fifoPlanner;
    private final SerpentineRoutePlanner routePlanner;

    @Transactional(readOnly = true)
    public OutboundPickingSuggestionResponse suggest(UUID tenantId,
                                                    UUID staffId,
                                                    UUID warehouseId,
                                                    List<OutboundPickingInputItem> inputItems) {
        Map<UUID, OutboundPickingInputItem> uniqueItems = validateAndIndexRequest(
                tenantId, warehouseId, inputItems);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireWmsAccess(tenantId, warehouseId);
        if (staffId != null) {
            accessService.requireActiveStaffAssignment(staffId, tenantId, warehouseId);
        }

        WarehouseLayout layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                .filter(candidate -> candidate.getWarehouse() != null
                        && warehouseId.equals(candidate.getWarehouse().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));

        Map<UUID, ProductSku> skusById = loadRequestedSkus(tenantId, uniqueItems);
        List<PickRouteCandidate> routeCandidates = new ArrayList<>();
        Map<UUID, StockBatch> selectedBatchesById = new HashMap<>();
        List<OutboundPickingItemResponse> itemResponses = new ArrayList<>();

        for (OutboundPickingInputItem inputItem : uniqueItems.values()) {
            ProductSku sku = skusById.get(inputItem.skuId());
            List<StockBatch> batches = stockBatchRepository
                    .findAllBySkuIdAndWarehouseIdAndIsActiveTrueAndIsDeletedFalse(
                            inputItem.skuId(), warehouseId)
                    .stream()
                    .filter(batch -> belongsToActiveLayout(batch, warehouseId, layout))
                    .toList();
            Map<UUID, StockBatch> batchesById = batches.stream()
                    .collect(java.util.stream.Collectors.toMap(StockBatch::getId,
                            batch -> batch, (left, right) -> left));
            FifoAllocationPlan fifoPlan = fifoPlanner.plan(
                    batches.stream()
                            .map(batch -> new FifoCandidate(
                                    batch.getId(),
                                    batch.getQuantity(),
                                    batch.getArrivalDate(),
                                    batch.getCreatedAt(),
                                    batch.isActive(),
                                    batch.isDeleted()))
                            .toList(),
                    inputItem.quantity());

            int allocatedQuantity = inputItem.quantity() - fifoPlan.shortageQuantity();
            itemResponses.add(new OutboundPickingItemResponse(
                    inputItem.skuId(),
                    inputItem.quantity(),
                    allocatedQuantity,
                    fifoPlan.shortageQuantity()));

            for (FifoAllocation allocation : fifoPlan.allocations()) {
                StockBatch batch = batchesById.get(allocation.stockBatchId());
                if (batch == null) {
                    throw new IllegalStateException("FIFO planner returned an unknown stock batch");
                }
                selectedBatchesById.put(batch.getId(), batch);
                routeCandidates.add(new PickRouteCandidate(
                        inputItem.skuId(),
                        batch.getId(),
                        batch.getRack().getId(),
                        batch.getRack().getCode(),
                        batch.getRack().getCoordinateX(),
                        batch.getRack().getCoordinateY(),
                        batch.getBin().getId(),
                        batch.getBin().getCode(),
                        batch.getBin().getCoordinateX(),
                        batch.getBin().getShelfLevel(),
                        allocation.quantity()));
            }
        }

        PickRoutePlan routePlan = routePlanner.plan(routeCandidates);
        Map<UUID, WarehouseBin> binsById = selectedBatchesById.values().stream()
                .collect(java.util.stream.Collectors.toMap(
                        batch -> batch.getBin().getId(),
                        StockBatch::getBin,
                        (left, right) -> left));
        List<OutboundPickStopResponse> stops = routePlan.stops().stream()
                .map(stop -> new OutboundPickStopResponse(
                        stop.sequence(),
                        stop.rackId(),
                        stop.rackCode(),
                        stop.binId(),
                        stop.binCode(),
                        binsById.get(stop.binId()).getShelfLevel(),
                        stop.allocations().stream()
                                .map(line -> toLineResponse(line, selectedBatchesById, skusById))
                                .toList()))
                .toList();

        boolean complete = itemResponses.stream()
                .allMatch(item -> item.shortageQuantity() == 0);
        return new OutboundPickingSuggestionResponse(
                warehouse.getId(),
                layout.getId(),
                STRATEGY,
                complete,
                List.copyOf(itemResponses),
                stops,
                routePlan.warnings());
    }

    private Map<UUID, OutboundPickingInputItem> validateAndIndexRequest(
            UUID tenantId, UUID warehouseId, List<OutboundPickingInputItem> inputItems) {
        if (tenantId == null || warehouseId == null || inputItems == null || inputItems.isEmpty()) {
            throw new BadRequestException("tenantId, warehouseId and at least one item are required");
        }

        Map<UUID, OutboundPickingInputItem> uniqueItems = new LinkedHashMap<>();
        for (OutboundPickingInputItem item : inputItems) {
            if (item == null || item.skuId() == null || item.quantity() <= 0) {
                throw new BadRequestException("Each outbound item must contain a positive quantity and skuId");
            }
            if (uniqueItems.putIfAbsent(item.skuId(), item) != null) {
                throw new BadRequestException("Each SKU may appear only once in an outbound request");
            }
        }
        return uniqueItems;
    }

    private Map<UUID, ProductSku> loadRequestedSkus(
            UUID tenantId, Map<UUID, OutboundPickingInputItem> inputItems) {
        Map<UUID, ProductSku> skusById = new LinkedHashMap<>();
        for (UUID skuId : inputItems.keySet()) {
            ProductSku sku = productSkuRepository
                    .findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId)
                    .filter(ProductSku::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));
            skusById.put(skuId, sku);
        }
        return skusById;
    }

    private boolean belongsToActiveLayout(StockBatch batch, UUID warehouseId, WarehouseLayout layout) {
        return batch.getWarehouse() != null
                && warehouseId.equals(batch.getWarehouse().getId())
                && batch.getRack() != null
                && batch.getBin() != null
                && batch.getRack().isActive()
                && !batch.getRack().isDeleted()
                && batch.getBin().isActive()
                && !batch.getBin().isDeleted()
                && batch.getRack().getLayout() != null
                && batch.getRack().getLayout().isActive()
                && !batch.getRack().getLayout().isDeleted()
                && layout.getId().equals(batch.getRack().getLayout().getId())
                && batch.getBin().getRack() != null
                && batch.getRack().getId().equals(batch.getBin().getRack().getId());
    }

    private OutboundPickLineResponse toLineResponse(
            PickRouteAllocation line,
            Map<UUID, StockBatch> selectedBatchesById,
            Map<UUID, ProductSku> skusById) {
        StockBatch batch = selectedBatchesById.get(line.stockBatchId());
        ProductSku sku = skusById.get(line.skuId());
        return new OutboundPickLineResponse(
                line.stockBatchId(),
                line.skuId(),
                sku.getSkuCode(),
                sku.getName(),
                batch.getArrivalDate(),
                line.quantity());
    }
}
