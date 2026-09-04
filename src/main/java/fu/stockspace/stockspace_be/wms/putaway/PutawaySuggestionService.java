package fu.stockspace.stockspace_be.wms.putaway;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
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
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoad;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calculates deterministic put-away suggestions from the active tenant
 * layout and current physical stock. This service never creates or updates
 * stock, receipts, transactions, reservations, racks, or bins.
 */
@Service
@RequiredArgsConstructor
public class PutawaySuggestionService {

    private static final String INSUFFICIENT_CAPACITY_WARNING =
            "Insufficient physical capacity for the requested quantity";

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final StockBatchRepository stockBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final TenantWarehouseAccessService accessService;
    private final PhysicalLoadCalculator physicalLoadCalculator;
    private final PutawaySuggestionPlanner planner;

    @Transactional(readOnly = true)
    public PutawaySuggestionResult suggest(UUID tenantId,
                                           UUID staffId,
                                           UUID warehouseId,
                                           List<PutawayInputItem> inputItems) {
        validateRequest(tenantId, warehouseId, inputItems);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireWmsAccess(tenantId, warehouseId);
        requireStaffAssignment(staffId, tenantId, warehouseId);

        WarehouseLayout layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));

        List<WarehouseRack> racks = rackRepository.findAllByLayoutId(layout.getId()).stream()
                .filter(rack -> rack.isActive() && !rack.isDeleted())
                .sorted(rackComparator())
                .toList();
        Map<UUID, WarehouseRack> racksById = racks.stream()
                .collect(Collectors.toMap(WarehouseRack::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));

        List<WarehouseBin> bins = binRepository.findAllByRackLayoutId(layout.getId()).stream()
                .filter(bin -> bin.isActive() && !bin.isDeleted())
                .filter(bin -> bin.getRack() != null && racksById.containsKey(bin.getRack().getId()))
                .sorted(binComparator())
                .toList();

        Map<UUID, ProductSku> skusById = loadRequestedSkus(tenantId, inputItems);
        List<PhysicalLoadLine> stockLines = stockBatchRepository
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId);

        Map<UUID, List<PhysicalLoadLine>> projectedByRack = groupLinesByRack(stockLines);
        Map<UUID, List<PhysicalLoadLine>> projectedByBin = groupLinesByBin(stockLines);

        Map<UUID, PutawayInputItem> uniqueItems = inputItems.stream()
                .collect(Collectors.toMap(PutawayInputItem::skuId, Function.identity(),
                        (left, right) -> {
                            throw new BadRequestException(
                                    "Each SKU may appear only once in a put-away request");
                        }, LinkedHashMap::new));

        List<PutawaySuggestionItem> suggestions = uniqueItems.values().stream()
                .sorted(Comparator.comparing(PutawayInputItem::skuId))
                .map(item -> suggestForItem(item, skusById.get(item.skuId()), bins, racksById,
                        projectedByRack, projectedByBin))
                .toList();

        Map<UUID, PutawaySuggestionItem> suggestionsBySku = suggestions.stream()
                .collect(Collectors.toMap(PutawaySuggestionItem::skuId, Function.identity()));
        List<PutawaySuggestionItem> responseItems = inputItems.stream()
                .map(item -> suggestionsBySku.get(item.skuId()))
                .toList();

        return new PutawaySuggestionResult(warehouse.getId(), layout.getId(), responseItems);
    }

    private PutawaySuggestionItem suggestForItem(
            PutawayInputItem item,
            ProductSku sku,
            List<WarehouseBin> bins,
            Map<UUID, WarehouseRack> racksById,
            Map<UUID, List<PhysicalLoadLine>> projectedByRack,
            Map<UUID, List<PhysicalLoadLine>> projectedByBin) {
        validateSkuPhysicalProperties(sku);

        List<CandidateState> states = new ArrayList<>();
        Map<UUID, PhysicalLoad> rackLoads = new HashMap<>();
        Map<UUID, PhysicalLoad> binLoads = new HashMap<>();
        for (WarehouseBin bin : bins) {
            WarehouseRack rack = racksById.get(bin.getRack().getId());
            if (rack == null) {
                continue;
            }

            List<PhysicalLoadLine> rackLines = projectedByRack.getOrDefault(rack.getId(), List.of());
            List<PhysicalLoadLine> binLines = projectedByBin.getOrDefault(bin.getId(), List.of());
            PhysicalLoad rackLoad = rackLoads.computeIfAbsent(rack.getId(), ignored ->
                    physicalLoadCalculator.calculate(rackLines, true, true));
            PhysicalLoad binLoad = binLoads.computeIfAbsent(bin.getId(), ignored ->
                    physicalLoadCalculator.calculate(binLines, true, true));
            int rackMaxQuantity = maxQuantity(rack.getMaxWeight(), rackLoad.weightKg(), sku.getUnitWeightKg(),
                    rack.getMaxVolume(), rackLoad.volumeM3(), sku.getUnitVolumeM3());
            int binMaxQuantity = maxQuantity(bin.getMaxWeight(), binLoad.weightKg(), sku.getUnitWeightKg(),
                    bin.getMaxVolume(), binLoad.volumeM3(), sku.getUnitVolumeM3());
            int maxQuantity = Math.min(rackMaxQuantity, binMaxQuantity);
            if (maxQuantity <= 0) {
                continue;
            }

            int plannedQuantity = Math.min(item.quantity(), maxQuantity);
            states.add(new CandidateState(
                    bin,
                    rack,
                    new PutawayCandidate(
                            rack.getId(),
                            bin.getId(),
                            rack.getCode(),
                            bin.getCode(),
                            positionOf(rack).add(positionOf(bin)),
                            containsSku(binLines, sku.getId()),
                            maxQuantity,
                            remainingCapacityRatio(rack, rackLoad, bin, binLoad,
                                    sku, plannedQuantity)),
                    capacitySnapshot(rack, rackLoad, bin, binLoad, sku, plannedQuantity)));
        }

        PutawayPlan plan = planner.plan(states.stream().map(CandidateState::candidate).toList(),
                item.quantity(), isHeavy(sku));
        Map<LocationKey, CandidateState> statesByLocation = states.stream()
                .collect(Collectors.toMap(state -> new LocationKey(state.rack().getId(), state.bin().getId()),
                        Function.identity()));

        List<PutawaySuggestedAllocation> allocations = plan.allocations().stream()
                .map(allocation -> {
                    CandidateState state = statesByLocation.get(
                            new LocationKey(allocation.rackId(), allocation.binId()));
                    addProjectedLine(projectedByRack, allocation.rackId(), sku, allocation.quantity());
                    addProjectedLine(projectedByBin, allocation.binId(), sku, allocation.quantity());
                    return new PutawaySuggestedAllocation(
                            allocation.rackId(), allocation.binId(), allocation.quantity(),
                            allocation.score(), allocation.reasons(),
                            state == null ? null : state.capacity());
                })
                .toList();

        return new PutawaySuggestionItem(
                sku.getId(), sku.getSkuCode(), sku.getName(), item.quantity(), allocations,
                plan.unallocatedQuantity(),
                plan.unallocatedQuantity() > 0 ? INSUFFICIENT_CAPACITY_WARNING : null);
    }

    private Map<UUID, ProductSku> loadRequestedSkus(UUID tenantId, List<PutawayInputItem> items) {
        Map<UUID, ProductSku> skus = new HashMap<>();
        for (PutawayInputItem item : items) {
            ProductSku sku = productSkuRepository
                    .findByIdAndTenantIdOrSystemAndIsDeletedFalse(item.skuId(), tenantId)
                    .filter(ProductSku::isActive)
                    .filter(candidate -> candidate.getTenant() == null
                            || tenantId.equals(candidate.getTenant().getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));
            skus.put(item.skuId(), sku);
        }
        return skus;
    }

    private void validateRequest(UUID tenantId, UUID warehouseId, List<PutawayInputItem> items) {
        if (tenantId == null || warehouseId == null || items == null || items.isEmpty()) {
            throw new BadRequestException("tenantId, warehouseId and at least one item are required");
        }
        if (items.stream().anyMatch(item -> item == null || item.skuId() == null || item.quantity() <= 0)) {
            throw new BadRequestException("Each put-away item must contain a positive quantity and skuId");
        }
    }

    private void validateSkuPhysicalProperties(ProductSku sku) {
        if (!positive(sku.getUnitWeightKg()) || !positive(sku.getUnitVolumeM3())) {
            throw new BadRequestException("SKU " + sku.getSkuCode()
                    + " must have positive unitWeightKg and unitVolumeM3 for put-away suggestions");
        }
    }

    private void requireStaffAssignment(UUID staffId, UUID tenantId, UUID warehouseId) {
        if (staffId != null) {
            accessService.requireActiveStaffAssignment(staffId, tenantId, warehouseId);
        }
    }

    private int maxQuantity(BigDecimal maxWeight,
                            BigDecimal currentWeight,
                            BigDecimal unitWeight,
                            BigDecimal maxVolume,
                            BigDecimal currentVolume,
                            BigDecimal unitVolume) {
        int byWeight = maxQuantityForDimension(maxWeight, currentWeight, unitWeight);
        int byVolume = maxQuantityForDimension(maxVolume, currentVolume, unitVolume);
        return Math.min(byWeight, byVolume);
    }

    private int maxQuantityForDimension(BigDecimal maximum, BigDecimal current,
                                       BigDecimal unitLoad) {
        if (!physicalLoadCalculator.isLimited(maximum)) {
            return Integer.MAX_VALUE;
        }
        BigDecimal available = maximum.subtract(current);
        if (available.signum() <= 0) {
            return 0;
        }
        BigDecimal quantity = available.divideToIntegralValue(unitLoad);
        if (quantity.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return quantity.intValue();
    }

    private BigDecimal remainingCapacityRatio(WarehouseRack rack,
                                              PhysicalLoad rackLoad,
                                              WarehouseBin bin,
                                              PhysicalLoad binLoad,
                                              ProductSku sku,
                                              int quantity) {
        BigDecimal ratio = null;
        ratio = minRatio(ratio, remainingRatio(rack.getMaxWeight(), rackLoad.weightKg(),
                sku.getUnitWeightKg(), quantity));
        ratio = minRatio(ratio, remainingRatio(rack.getMaxVolume(), rackLoad.volumeM3(),
                sku.getUnitVolumeM3(), quantity));
        ratio = minRatio(ratio, remainingRatio(bin.getMaxWeight(), binLoad.weightKg(),
                sku.getUnitWeightKg(), quantity));
        ratio = minRatio(ratio, remainingRatio(bin.getMaxVolume(), binLoad.volumeM3(),
                sku.getUnitVolumeM3(), quantity));
        return ratio;
    }

    private BigDecimal remainingRatio(BigDecimal maximum, BigDecimal current,
                                      BigDecimal unitLoad, int quantity) {
        if (!physicalLoadCalculator.isLimited(maximum)) {
            return null;
        }
        BigDecimal remaining = maximum.subtract(current)
                .subtract(unitLoad.multiply(BigDecimal.valueOf(quantity)));
        return remaining.max(BigDecimal.ZERO)
                .divide(maximum, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal minRatio(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.compareTo(current) < 0 ? candidate : current;
    }

    private PutawayCapacitySnapshot capacitySnapshot(WarehouseRack rack,
                                                     PhysicalLoad rackLoad,
                                                     WarehouseBin bin,
                                                     PhysicalLoad binLoad,
                                                     ProductSku sku,
                                                     int quantity) {
        return new PutawayCapacitySnapshot(
                locationCapacity(rack.getId(), rack.getName(), rack.getMaxWeight(), rack.getMaxVolume(),
                        rackLoad, sku, quantity),
                locationCapacity(bin.getId(), bin.getName(), bin.getMaxWeight(), bin.getMaxVolume(),
                        binLoad, sku, quantity));
    }

    private PutawayLocationCapacity locationCapacity(UUID locationId,
                                                      String name,
                                                      BigDecimal maxWeight,
                                                      BigDecimal maxVolume,
                                                      PhysicalLoad current,
                                                      ProductSku sku,
                                                      int quantity) {
        BigDecimal addedWeight = sku.getUnitWeightKg().multiply(BigDecimal.valueOf(quantity));
        BigDecimal addedVolume = sku.getUnitVolumeM3().multiply(BigDecimal.valueOf(quantity));
        return new PutawayLocationCapacity(
                locationId,
                name,
                current.weightKg(),
                current.volumeM3(),
                maxWeight,
                maxVolume,
                remainingAfter(maxWeight, current.weightKg(), addedWeight),
                remainingAfter(maxVolume, current.volumeM3(), addedVolume));
    }

    private BigDecimal remainingAfter(BigDecimal maximum, BigDecimal current, BigDecimal added) {
        return physicalLoadCalculator.isLimited(maximum) ? maximum.subtract(current).subtract(added) : null;
    }

    private boolean containsSku(List<PhysicalLoadLine> lines, UUID skuId) {
        return lines.stream().anyMatch(line -> skuId.equals(line.skuId()) && line.quantity() > 0);
    }

    private void addProjectedLine(Map<UUID, List<PhysicalLoadLine>> projected,
                                  UUID locationId,
                                  ProductSku sku,
                                  int quantity) {
        projected.computeIfAbsent(locationId, ignored -> new ArrayList<>())
                .add(new PhysicalLoadLine(null, null, sku.getId(), sku.getSkuCode(), sku.getName(),
                        sku.getUnitWeightKg(), sku.getUnitVolumeM3(), quantity));
    }

    private Map<UUID, List<PhysicalLoadLine>> groupLinesByRack(List<PhysicalLoadLine> lines) {
        return lines.stream().filter(line -> line.rackId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::rackId,
                        LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
    }

    private Map<UUID, List<PhysicalLoadLine>> groupLinesByBin(List<PhysicalLoadLine> lines) {
        return lines.stream().filter(line -> line.binId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::binId,
                        LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
    }

    private Comparator<WarehouseRack> rackComparator() {
        return Comparator.comparing(WarehouseRack::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(WarehouseRack::getId);
    }

    private Comparator<WarehouseBin> binComparator() {
        return Comparator.comparing((WarehouseBin bin) -> bin.getRack() == null
                        ? null : bin.getRack().getCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(WarehouseBin::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(WarehouseBin::getId);
    }

    private BigDecimal positionOf(WarehouseRack rack) {
        return rack.getPositionZ() == null ? BigDecimal.ZERO : rack.getPositionZ();
    }

    private BigDecimal positionOf(WarehouseBin bin) {
        return bin.getPositionZ() == null ? BigDecimal.ZERO : bin.getPositionZ();
    }

    private boolean isHeavy(ProductSku sku) {
        return sku.getUnitWeightKg().signum() > 0;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private record CandidateState(WarehouseBin bin,
                                  WarehouseRack rack,
                                  PutawayCandidate candidate,
                                  PutawayCapacitySnapshot capacity) {
    }

    private record LocationKey(UUID rackId, UUID binId) {
    }
}
