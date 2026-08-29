package fu.stockspace.stockspace_be.wms.capacity.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoad;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
import fu.stockspace.stockspace_be.wms.capacity.SkuPhysicalLoad;
import fu.stockspace.stockspace_be.wms.capacity.dto.BinCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.RackCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.SkuCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.WarehouseLayoutCapacityResponse;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseCapacityService {

    private static final int UTILIZATION_SCALE = 2;
    private static final int DIVISION_SCALE = 8;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final StockBatchRepository stockBatchRepository;
    private final TenantWarehouseAccessService accessService;
    private final PhysicalLoadCalculator physicalLoadCalculator;

    @Transactional(readOnly = true)
    public WarehouseLayoutCapacityResponse getCapacity(UUID tenantId, UUID warehouseId, UUID staffId) {
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireObservationAccess(tenantId, warehouseId, staffId);

        WarehouseLayout layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                .orElseGet(() -> layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)
                        .filter(candidate -> candidate.isActive() && !candidate.isDeleted())
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND)));

        List<PhysicalLoadLine> stockLines = stockBatchRepository
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId);
        Map<UUID, List<PhysicalLoadLine>> linesByRack = stockLines.stream()
                .filter(line -> line.rackId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::rackId));
        Map<UUID, List<PhysicalLoadLine>> linesByBin = stockLines.stream()
                .filter(line -> line.binId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::binId));

        List<WarehouseBin> bins = binRepository.findAllByRackLayoutId(layout.getId());
        Map<UUID, List<WarehouseBin>> binsByRack = bins.stream()
                .filter(bin -> bin.getRack() != null)
                .collect(Collectors.groupingBy(bin -> bin.getRack().getId()));
        List<WarehouseRack> racks = rackRepository.findAllByLayoutId(layout.getId()).stream()
                .sorted(Comparator.comparing(WarehouseRack::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        List<RackCapacityResponse> rackResponses = racks.stream()
                .map(rack -> mapRack(rack, binsByRack.getOrDefault(rack.getId(), List.of()),
                        linesByRack, linesByBin))
                .toList();

        return WarehouseLayoutCapacityResponse.builder()
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .layoutId(layout.getId())
                .racks(rackResponses)
                .build();
    }

    private void requireObservationAccess(UUID tenantId, UUID warehouseId, UUID staffId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (staffId != null) {
            accessService.requireActiveStaffAssignment(staffId, tenantId, warehouseId);
        }
    }

    private RackCapacityResponse mapRack(WarehouseRack rack,
                                         List<WarehouseBin> bins,
                                         Map<UUID, List<PhysicalLoadLine>> linesByRack,
                                         Map<UUID, List<PhysicalLoadLine>> linesByBin) {
        List<PhysicalLoadLine> rackLines = linesByRack.getOrDefault(rack.getId(), List.of());
        PhysicalLoad rackLoad = physicalLoadCalculator.calculate(rackLines, true, true);
        List<SkuCapacityResponse> storedSkus = mapSkuLoads(
                physicalLoadCalculator.summarizeBySku(rackLines));

        List<BinCapacityResponse> binResponses = bins.stream()
                .sorted(Comparator.comparing(WarehouseBin::getCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(bin -> mapBin(bin, linesByBin.getOrDefault(bin.getId(), List.of())))
                .toList();

        return RackCapacityResponse.builder()
                .rackId(rack.getId())
                .rackName(rack.getName())
                .currentWeightKg(rackLoad.weightKg())
                .currentVolumeM3(rackLoad.volumeM3())
                .maxWeightKg(rack.getMaxWeight())
                .maxVolumeM3(rack.getMaxVolume())
                .remainingWeightKg(remaining(rack.getMaxWeight(), rackLoad.weightKg()))
                .remainingVolumeM3(remaining(rack.getMaxVolume(), rackLoad.volumeM3()))
                .weightUtilizationPercent(utilization(rack.getMaxWeight(), rackLoad.weightKg()))
                .volumeUtilizationPercent(utilization(rack.getMaxVolume(), rackLoad.volumeM3()))
                .capacityStatus(status(rack.getMaxWeight(), rack.getMaxVolume(), rackLoad))
                .storedSkus(storedSkus)
                .bins(binResponses)
                .build();
    }

    private BinCapacityResponse mapBin(WarehouseBin bin,
                                        List<PhysicalLoadLine> binLines) {
        PhysicalLoad binLoad = physicalLoadCalculator.calculate(binLines, true, true);
        return BinCapacityResponse.builder()
                .binId(bin.getId())
                .binName(bin.getName())
                .currentWeightKg(binLoad.weightKg())
                .currentVolumeM3(binLoad.volumeM3())
                .maxWeightKg(bin.getMaxWeight())
                .maxVolumeM3(bin.getMaxVolume())
                .remainingWeightKg(remaining(bin.getMaxWeight(), binLoad.weightKg()))
                .remainingVolumeM3(remaining(bin.getMaxVolume(), binLoad.volumeM3()))
                .weightUtilizationPercent(utilization(bin.getMaxWeight(), binLoad.weightKg()))
                .volumeUtilizationPercent(utilization(bin.getMaxVolume(), binLoad.volumeM3()))
                .capacityStatus(status(bin.getMaxWeight(), bin.getMaxVolume(), binLoad))
                .storedSkus(mapSkuLoads(physicalLoadCalculator.summarizeBySku(binLines)))
                .build();
    }

    private List<SkuCapacityResponse> mapSkuLoads(List<SkuPhysicalLoad> skuLoads) {
        return skuLoads.stream()
                .sorted(Comparator.comparing(SkuPhysicalLoad::skuCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(load -> SkuCapacityResponse.builder()
                        .skuId(load.skuId())
                        .skuCode(load.skuCode())
                        .skuName(load.skuName())
                        .quantity(load.quantity())
                        .weightKg(load.weightKg())
                        .volumeM3(load.volumeM3())
                        .build())
                .toList();
    }

    private BigDecimal remaining(BigDecimal maximum, BigDecimal current) {
        return physicalLoadCalculator.isLimited(maximum) ? maximum.subtract(current) : null;
    }

    private BigDecimal utilization(BigDecimal maximum, BigDecimal current) {
        if (!physicalLoadCalculator.isLimited(maximum)) {
            return null;
        }
        return current.divide(maximum, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT)
                .setScale(UTILIZATION_SCALE, RoundingMode.HALF_UP);
    }

    private CapacityStatus status(BigDecimal maxWeight, BigDecimal maxVolume, PhysicalLoad load) {
        boolean weightLimited = physicalLoadCalculator.isLimited(maxWeight);
        boolean volumeLimited = physicalLoadCalculator.isLimited(maxVolume);
        boolean overWeight = weightLimited && load.weightKg().compareTo(maxWeight) > 0;
        boolean overVolume = volumeLimited && load.volumeM3().compareTo(maxVolume) > 0;
        if (overWeight || overVolume) {
            return CapacityStatus.OVER_CAPACITY;
        }
        if (load.weightKg().signum() == 0 && load.volumeM3().signum() == 0) {
            return CapacityStatus.EMPTY;
        }
        boolean fullWeight = weightLimited && load.weightKg().compareTo(maxWeight) >= 0;
        boolean fullVolume = volumeLimited && load.volumeM3().compareTo(maxVolume) >= 0;
        return fullWeight || fullVolume ? CapacityStatus.FULL : CapacityStatus.AVAILABLE;
    }
}
