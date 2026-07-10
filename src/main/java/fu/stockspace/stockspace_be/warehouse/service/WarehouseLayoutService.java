package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseZone;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseZoneRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseLayoutService {

    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseZoneRepository zoneRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final RentalContractRepository contractRepository;
    private final StockBatchRepository stockBatchRepository;

    /**
     * Lấy toàn bộ cây sơ đồ layout kho (Zone -> Rack -> Bin)
     */
    @Transactional(readOnly = true)
    public WarehouseLayoutResponse getLayoutTree(UUID warehouseId, UUID userId, String role) {
        log.info("Fetching layout tree for warehouse: {}, user: {}, role: {}", warehouseId, userId, role);

        WarehouseLayout layout = null;

        if ("OWNER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
        } else if ("TENANT".equalsIgnoreCase(role)) {
            // Thử tìm layout tùy biến của tenant này
            layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId).orElse(null);
            if (layout == null) {
                // Nếu chưa có layout tùy biến, dùng layout mặc định làm cơ sở
                layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            }
        } else {
            // Khách vãng lai public hoặc guest
            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
        }

        if (layout == null) {
            throw new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND);
        }

        return mapToLayoutResponse(layout);
    }

    /**
     * Nhân bản sơ đồ mặc định của Owner sang Tenant
     */
    @Transactional
    public void cloneLayout(UUID warehouseId, UUID tenantId) {
        log.info("Cloning layout of warehouse {} to tenant {}", warehouseId, tenantId);

        // Kiểm tra xem tenant đã có bản clone chưa để tránh lặp
        Optional<WarehouseLayout> existing = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId);
        if (existing.isPresent()) {
            log.info("Tenant {} already has a layout clone for warehouse {}", tenantId, warehouseId);
            return;
        }

        Optional<WarehouseLayout> defaultLayoutOpt = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId);
        if (defaultLayoutOpt.isEmpty()) {
            log.warn("Warehouse {} has no default layout to clone. Skipping cloning.", warehouseId);
            return;
        }
        WarehouseLayout defaultLayout = defaultLayoutOpt.get();

        User tenantUser = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        WarehouseLayout tenantLayout = WarehouseLayout.builder()
                .warehouse(defaultLayout.getWarehouse())
                .tenant(tenantUser)
                .isDefault(false)
                .width(defaultLayout.getWidth())
                .height(defaultLayout.getHeight())
                .build();
        tenantLayout = layoutRepository.save(tenantLayout);

        // Lấy dữ liệu default
        List<WarehouseZone> defaultZones = zoneRepository.findAllByLayoutId(defaultLayout.getId());
        List<WarehouseRack> defaultRacks = rackRepository.findAllByZoneLayoutId(defaultLayout.getId());
        List<WarehouseBin> defaultBins = binRepository.findAllByRackZoneLayoutId(defaultLayout.getId());

        Map<UUID, List<WarehouseRack>> racksByZone = defaultRacks.stream()
                .collect(Collectors.groupingBy(r -> r.getZone().getId()));
        Map<UUID, List<WarehouseBin>> binsByRack = defaultBins.stream()
                .collect(Collectors.groupingBy(b -> b.getRack().getId()));

        for (WarehouseZone zone : defaultZones) {
            WarehouseZone cloneZone = WarehouseZone.builder()
                    .layout(tenantLayout)
                    .name(zone.getName())
                    .code(zone.getCode())
                    .maxWeight(zone.getMaxWeight())
                    .maxVolume(zone.getMaxVolume())
                    .coordinateX(zone.getCoordinateX())
                    .coordinateY(zone.getCoordinateY())
                    .width(zone.getWidth())
                    .height(zone.getHeight())
                    .build();
            cloneZone = zoneRepository.save(cloneZone);

            List<WarehouseRack> zoneRacks = racksByZone.getOrDefault(zone.getId(), Collections.emptyList());
            for (WarehouseRack rack : zoneRacks) {
                WarehouseRack cloneRack = WarehouseRack.builder()
                        .zone(cloneZone)
                        .name(rack.getName())
                        .code(rack.getCode())
                        .maxWeight(rack.getMaxWeight())
                        .maxVolume(rack.getMaxVolume())
                        .coordinateX(rack.getCoordinateX())
                        .coordinateY(rack.getCoordinateY())
                        .width(rack.getWidth())
                        .height(rack.getHeight())
                        .build();
                cloneRack = rackRepository.save(cloneRack);

                List<WarehouseBin> rackBins = binsByRack.getOrDefault(rack.getId(), Collections.emptyList());
                for (WarehouseBin bin : rackBins) {
                    WarehouseBin cloneBin = WarehouseBin.builder()
                            .rack(cloneRack)
                            .name(bin.getName())
                            .code(bin.getCode())
                            .maxWeight(bin.getMaxWeight())
                            .maxVolume(bin.getMaxVolume())
                            .coordinateX(bin.getCoordinateX())
                            .coordinateY(bin.getCoordinateY())
                            .width(bin.getWidth())
                            .height(bin.getHeight())
                            .build();
                    binRepository.save(cloneBin);
                }
            }
        }
        log.info("Layout cloning completed successfully.");
    }

    /**
     * Smart Sync lưu sơ đồ hàng loạt
     */
    @Transactional
    public WarehouseLayoutResponse saveLayoutBulk(UUID warehouseId, UUID userId, String role, BulkLayoutSaveRequest request) {
        log.info("Performing bulk save for warehouse layout: {} by user: {}, role: {}", warehouseId, userId, role);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        WarehouseLayout layout = null;

        if ("OWNER".equalsIgnoreCase(role)) {
            // Kiểm tra xem owner có sở hữu warehouse này không
            if (!warehouse.getOwner().getId().equals(userId)) {
                throw new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED);
            }
            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            if (layout == null) {
                layout = WarehouseLayout.builder()
                        .warehouse(warehouse)
                        .isDefault(true)
                        .width(request.getWidth())
                        .height(request.getHeight())
                        .build();
            } else {
                layout.setWidth(request.getWidth());
                layout.setHeight(request.getHeight());
            }
            layout = layoutRepository.save(layout);

        } else if ("TENANT".equalsIgnoreCase(role)) {
            // Kiểm tra xem tenant có hợp đồng thuê hoạt động (ACTIVE) không
            boolean hasActiveContract = contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(userId, warehouseId);
            if (!hasActiveContract) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }

            layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId).orElse(null);
            if (layout == null) {
                User tenantUser = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
                layout = WarehouseLayout.builder()
                        .warehouse(warehouse)
                        .tenant(tenantUser)
                        .isDefault(false)
                        .width(request.getWidth())
                        .height(request.getHeight())
                        .build();
            } else {
                layout.setWidth(request.getWidth());
                layout.setHeight(request.getHeight());
            }
            layout = layoutRepository.save(layout);

        } else {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        // ==================== Smart Sync Synchronization ====================
        List<WarehouseZone> dbZones = zoneRepository.findAllByLayoutId(layout.getId());
        List<WarehouseRack> dbRacks = rackRepository.findAllByZoneLayoutId(layout.getId());
        List<WarehouseBin> dbBins = binRepository.findAllByRackZoneLayoutId(layout.getId());

        Map<UUID, WarehouseZone> dbZoneMap = dbZones.stream().collect(Collectors.toMap(WarehouseZone::getId, z -> z));
        Map<UUID, WarehouseRack> dbRackMap = dbRacks.stream().collect(Collectors.toMap(WarehouseRack::getId, r -> r));
        Map<UUID, WarehouseBin> dbBinMap = dbBins.stream().collect(Collectors.toMap(WarehouseBin::getId, b -> b));

        Set<UUID> reqZoneIds = new HashSet<>();
        Set<UUID> reqRackIds = new HashSet<>();
        Set<UUID> reqBinIds = new HashSet<>();

        if (request.getZones() != null) {
            for (ZoneSaveRequest zReq : request.getZones()) {
                if (zReq.getId() != null) reqZoneIds.add(zReq.getId());
                if (zReq.getRacks() != null) {
                    for (RackSaveRequest rReq : zReq.getRacks()) {
                        if (rReq.getId() != null) reqRackIds.add(rReq.getId());
                        if (rReq.getBins() != null) {
                            for (BinSaveRequest bReq : rReq.getBins()) {
                                if (bReq.getId() != null) reqBinIds.add(bReq.getId());
                            }
                        }
                    }
                }
            }
        }

        // Tìm phần tử bị xoá
        List<UUID> zonesToDelete = dbZones.stream().map(WarehouseZone::getId)
                .filter(id -> !reqZoneIds.contains(id)).collect(Collectors.toList());

        List<UUID> racksToDelete = dbRacks.stream().map(WarehouseRack::getId)
                .filter(id -> !reqRackIds.contains(id)).collect(Collectors.toList());

        List<UUID> binsToDelete = dbBins.stream().map(WarehouseBin::getId)
                .filter(id -> !reqBinIds.contains(id)).collect(Collectors.toList());

        // ==================== Inventory Guards ====================
        for (UUID binId : binsToDelete) {
            if (stockBatchRepository.existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(binId, 0)) {
                WarehouseBin bin = dbBinMap.get(binId);
                String name = bin != null ? bin.getName() : binId.toString();
                throw new BadRequestException(ErrorCode.WAREHOUSE_BIN_NOT_EMPTY,
                        "Không thể xóa ô chứa " + name + " vì vẫn còn hàng tồn kho");
            }
        }
        for (UUID rackId : racksToDelete) {
            if (stockBatchRepository.existsByRackIdAndQuantityGreaterThanAndIsDeletedFalse(rackId, 0)) {
                WarehouseRack rack = dbRackMap.get(rackId);
                String name = rack != null ? rack.getName() : rackId.toString();
                throw new BadRequestException(ErrorCode.WAREHOUSE_BIN_NOT_EMPTY,
                        "Không thể xóa kệ hàng " + name + " vì vẫn còn hàng tồn kho");
            }
        }
        for (UUID zoneId : zonesToDelete) {
            if (stockBatchRepository.existsByZoneIdAndQuantityGreaterThanAndIsDeletedFalse(zoneId, 0)) {
                WarehouseZone zone = dbZoneMap.get(zoneId);
                String name = zone != null ? zone.getName() : zoneId.toString();
                throw new BadRequestException(ErrorCode.WAREHOUSE_BIN_NOT_EMPTY,
                        "Không thể xóa khu vực " + name + " vì vẫn còn hàng tồn kho");
            }
        }

        // ==================== Perform Delete ====================
        for (UUID binId : binsToDelete) {
            binRepository.deleteById(binId);
        }
        for (UUID rackId : racksToDelete) {
            rackRepository.deleteById(rackId);
        }
        for (UUID zoneId : zonesToDelete) {
            zoneRepository.deleteById(zoneId);
        }

        // ==================== Perform Save & Update ====================
        if (request.getZones() != null) {
            for (ZoneSaveRequest zReq : request.getZones()) {
                // Ràng buộc biên: Zone trong Layout
                if (zReq.getCoordinateX() < 0 || zReq.getCoordinateY() < 0 ||
                        zReq.getCoordinateX() + zReq.getWidth() > layout.getWidth() ||
                        zReq.getCoordinateY() + zReq.getHeight() > layout.getHeight()) {
                    throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES,
                            "Tọa độ/Kích thước Zone " + zReq.getName() + " vượt quá biên giới hạn của Layout (" + layout.getWidth() + "x" + layout.getHeight() + ")");
                }

                WarehouseZone zone;
                if (zReq.getId() != null && dbZoneMap.containsKey(zReq.getId())) {
                    zone = dbZoneMap.get(zReq.getId());
                    zone.setName(zReq.getName());
                    zone.setCode(zReq.getCode());
                    zone.setMaxWeight(zReq.getMaxWeight());
                    zone.setMaxVolume(zReq.getMaxVolume());
                    zone.setCoordinateX(zReq.getCoordinateX());
                    zone.setCoordinateY(zReq.getCoordinateY());
                    zone.setWidth(zReq.getWidth());
                    zone.setHeight(zReq.getHeight());
                } else {
                    zone = WarehouseZone.builder()
                            .layout(layout)
                            .name(zReq.getName())
                            .code(zReq.getCode())
                            .maxWeight(zReq.getMaxWeight())
                            .maxVolume(zReq.getMaxVolume())
                            .coordinateX(zReq.getCoordinateX())
                            .coordinateY(zReq.getCoordinateY())
                            .width(zReq.getWidth())
                            .height(zReq.getHeight())
                            .build();
                }
                zone = zoneRepository.save(zone);

                if (zReq.getRacks() != null) {
                    for (RackSaveRequest rReq : zReq.getRacks()) {
                        // Ràng buộc biên: Rack trong Zone
                        if (rReq.getCoordinateX() < 0 || rReq.getCoordinateY() < 0 ||
                                rReq.getCoordinateX() + rReq.getWidth() > zReq.getWidth() ||
                                rReq.getCoordinateY() + rReq.getHeight() > zReq.getHeight()) {
                            throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES,
                                    "Tọa độ/Kích thước Rack " + rReq.getName() + " vượt quá biên giới hạn của Zone " + zReq.getName() + " (" + zReq.getWidth() + "x" + zReq.getHeight() + ")");
                        }

                        WarehouseRack rack;
                        if (rReq.getId() != null && dbRackMap.containsKey(rReq.getId())) {
                            rack = dbRackMap.get(rReq.getId());
                            rack.setZone(zone);
                            rack.setName(rReq.getName());
                            rack.setCode(rReq.getCode());
                            rack.setMaxWeight(rReq.getMaxWeight());
                            rack.setMaxVolume(rReq.getMaxVolume());
                            rack.setCoordinateX(rReq.getCoordinateX());
                            rack.setCoordinateY(rReq.getCoordinateY());
                            rack.setWidth(rReq.getWidth());
                            rack.setHeight(rReq.getHeight());
                        } else {
                            rack = WarehouseRack.builder()
                                    .zone(zone)
                                    .name(rReq.getName())
                                    .code(rReq.getCode())
                                    .maxWeight(rReq.getMaxWeight())
                                    .maxVolume(rReq.getMaxVolume())
                                    .coordinateX(rReq.getCoordinateX())
                                    .coordinateY(rReq.getCoordinateY())
                                    .width(rReq.getWidth())
                                    .height(rReq.getHeight())
                                    .build();
                        }
                        rack = rackRepository.save(rack);

                        if (rReq.getBins() != null) {
                            for (BinSaveRequest bReq : rReq.getBins()) {
                                // Ràng buộc biên: Bin trong Rack
                                if (bReq.getCoordinateX() < 0 || bReq.getCoordinateY() < 0 ||
                                        bReq.getCoordinateX() + bReq.getWidth() > rReq.getWidth() ||
                                        bReq.getCoordinateY() + bReq.getHeight() > rReq.getHeight()) {
                                    throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES,
                                            "Tọa độ/Kích thước Bin " + bReq.getName() + " vượt quá biên giới hạn của Rack " + rReq.getName() + " (" + rReq.getWidth() + "x" + rReq.getHeight() + ")");
                                }

                                WarehouseBin bin;
                                if (bReq.getId() != null && dbBinMap.containsKey(bReq.getId())) {
                                    bin = dbBinMap.get(bReq.getId());
                                    bin.setRack(rack);
                                    bin.setName(bReq.getName());
                                    bin.setCode(bReq.getCode());
                                    bin.setMaxWeight(bReq.getMaxWeight());
                                    bin.setMaxVolume(bReq.getMaxVolume());
                                    bin.setCoordinateX(bReq.getCoordinateX());
                                    bin.setCoordinateY(bReq.getCoordinateY());
                                    bin.setWidth(bReq.getWidth());
                                    bin.setHeight(bReq.getHeight());
                                } else {
                                    bin = WarehouseBin.builder()
                                            .rack(rack)
                                            .name(bReq.getName())
                                            .code(bReq.getCode())
                                            .maxWeight(bReq.getMaxWeight())
                                            .maxVolume(bReq.getMaxVolume())
                                            .coordinateX(bReq.getCoordinateX())
                                            .coordinateY(bReq.getCoordinateY())
                                            .width(bReq.getWidth())
                                            .height(bReq.getHeight())
                                            .build();
                                }
                                binRepository.save(bin);
                            }
                        }
                    }
                }
            }
        }

        return mapToLayoutResponse(layout);
    }

    /**
     * Helper ánh xạ đệ quy từ Entity sang Response DTO
     */
    private WarehouseLayoutResponse mapToLayoutResponse(WarehouseLayout layout) {
        List<WarehouseZone> zones = zoneRepository.findAllByLayoutId(layout.getId());
        List<WarehouseRack> racks = rackRepository.findAllByZoneLayoutId(layout.getId());
        List<WarehouseBin> bins = binRepository.findAllByRackZoneLayoutId(layout.getId());

        Map<UUID, List<WarehouseRack>> racksByZone = racks.stream()
                .collect(Collectors.groupingBy(r -> r.getZone().getId()));
        Map<UUID, List<WarehouseBin>> binsByRack = bins.stream()
                .collect(Collectors.groupingBy(b -> b.getRack().getId()));

        List<ZoneResponse> zoneResponses = zones.stream().map(zone -> {
            List<WarehouseRack> zoneRacks = racksByZone.getOrDefault(zone.getId(), Collections.emptyList());
            List<RackResponse> rackResponses = zoneRacks.stream().map(rack -> {
                List<WarehouseBin> rackBins = binsByRack.getOrDefault(rack.getId(), Collections.emptyList());
                List<WarehouseBinResponse> binResponses = rackBins.stream().map(bin ->
                        WarehouseBinResponse.builder()
                                .id(bin.getId())
                                .rackId(rack.getId())
                                .name(bin.getName())
                                .code(bin.getCode())
                                .maxWeight(bin.getMaxWeight())
                                .maxVolume(bin.getMaxVolume())
                                .coordinateX(bin.getCoordinateX())
                                .coordinateY(bin.getCoordinateY())
                                .width(bin.getWidth())
                                .height(bin.getHeight())
                                .build()
                ).collect(Collectors.toList());

                return RackResponse.builder()
                        .id(rack.getId())
                        .zoneId(zone.getId())
                        .name(rack.getName())
                        .code(rack.getCode())
                        .maxWeight(rack.getMaxWeight())
                        .maxVolume(rack.getMaxVolume())
                        .coordinateX(rack.getCoordinateX())
                        .coordinateY(rack.getCoordinateY())
                        .width(rack.getWidth())
                        .height(rack.getHeight())
                        .bins(binResponses)
                        .build();
            }).collect(Collectors.toList());

            return ZoneResponse.builder()
                    .id(zone.getId())
                    .layoutId(layout.getId())
                    .name(zone.getName())
                    .code(zone.getCode())
                    .maxWeight(zone.getMaxWeight())
                    .maxVolume(zone.getMaxVolume())
                    .coordinateX(zone.getCoordinateX())
                    .coordinateY(zone.getCoordinateY())
                    .width(zone.getWidth())
                    .height(zone.getHeight())
                    .racks(rackResponses)
                    .build();
        }).collect(Collectors.toList());

        return WarehouseLayoutResponse.builder()
                .id(layout.getId())
                .warehouseId(layout.getWarehouse().getId())
                .tenantId(layout.getTenant() != null ? layout.getTenant().getId() : null)
                .isDefault(layout.isDefault())
                .width(layout.getWidth())
                .height(layout.getHeight())
                .zones(zoneResponses)
                .build();
    }
}
