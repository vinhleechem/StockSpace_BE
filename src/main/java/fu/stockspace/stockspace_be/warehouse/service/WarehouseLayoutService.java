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
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service quản lý sơ đồ Layout kho bãi (3 tầng: Layout -> Rack -> Bin)
 * Hỗ trợ 2D & 3D rendering (positionX, positionY, positionZ, rotation, dimensions)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseLayoutService {

    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final RentalContractRepository contractRepository;
    private final StockBatchRepository stockBatchRepository;

    /**
     * Lấy toàn bộ cây sơ đồ layout kho (Rack -> Bin) và số liệu thống kê
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
     * Nhân bản sơ đồ mặc định của Owner sang Tenant khi thuê kho thành công
     */
    @Transactional
    public void cloneLayout(UUID warehouseId, UUID tenantId) {
        log.info("Cloning layout of warehouse {} to tenant {}", warehouseId, tenantId);

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
                .length(defaultLayout.getLength())
                .height(defaultLayout.getHeight())
                .build();
        tenantLayout = layoutRepository.save(tenantLayout);

        // Lấy dữ liệu default racks & bins
        List<WarehouseRack> defaultRacks = rackRepository.findAllByLayoutId(defaultLayout.getId());
        List<WarehouseBin> defaultBins = binRepository.findAllByRackLayoutId(defaultLayout.getId());

        Map<UUID, List<WarehouseBin>> binsByRack = defaultBins.stream()
                .collect(Collectors.groupingBy(b -> b.getRack().getId()));

        for (WarehouseRack rack : defaultRacks) {
            WarehouseRack cloneRack = WarehouseRack.builder()
                    .layout(tenantLayout)
                    .zoneName(rack.getZoneName())
                    .zoneCode(rack.getZoneCode())
                    .name(rack.getName())
                    .code(rack.getCode())
                    .maxWeight(rack.getMaxWeight())
                    .maxVolume(rack.getMaxVolume())
                    .coordinateX(rack.getCoordinateX())
                    .coordinateY(rack.getCoordinateY())
                    .positionZ(rack.getPositionZ())
                    .rotation(rack.getRotation())
                    .width(rack.getWidth())
                    .length(rack.getLength())
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
                        .shelfLevel(bin.getShelfLevel())
                        .coordinateX(bin.getCoordinateX())
                        .coordinateY(bin.getCoordinateY())
                        .positionZ(bin.getPositionZ())
                        .width(bin.getWidth())
                        .length(bin.getLength())
                        .height(bin.getHeight())
                        .build();
                binRepository.save(cloneBin);
            }
        }
        log.info("Layout cloning completed successfully.");
    }

    /**
     * Smart Sync lưu sơ đồ hàng loạt (Chỉ Owner có quyền thêm/xóa/sửa kích thước, Tenant chỉ được di chuyển/xoay tọa độ)
     */
    @Transactional
    public WarehouseLayoutResponse saveLayoutBulk(UUID warehouseId, UUID userId, String role, BulkLayoutSaveRequest request) {
        log.info("Performing bulk save for warehouse layout: {} by user: {}, role: {}", warehouseId, userId, role);

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        WarehouseLayout layout = null;
        boolean isTenantRole = "TENANT".equalsIgnoreCase(role);

        if ("OWNER".equalsIgnoreCase(role)) {
            if (!warehouse.getOwner().getId().equals(userId)) {
                throw new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED);
            }
            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            if (layout == null) {
                layout = WarehouseLayout.builder()
                        .warehouse(warehouse)
                        .isDefault(true)
                        .width(request.getWidth())
                        .length(request.getLength() != null ? request.getLength() : 100)
                        .height(request.getHeight())
                        .build();
            } else {
                layout.setWidth(request.getWidth());
                if (request.getLength() != null) layout.setLength(request.getLength());
                layout.setHeight(request.getHeight());
            }
            layout = layoutRepository.save(layout);

        } else if (isTenantRole) {
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
                        .length(request.getLength() != null ? request.getLength() : 100)
                        .height(request.getHeight())
                        .build();
                layout = layoutRepository.save(layout);
            }
            // Tenant không được sửa kích thước tổng không gian kho (layout width/length/height giữ nguyên)
        } else {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        // Fetch DB state
        List<WarehouseRack> dbRacks = rackRepository.findAllByLayoutId(layout.getId());
        List<WarehouseBin> dbBins = binRepository.findAllByRackLayoutId(layout.getId());

        Map<UUID, WarehouseRack> dbRackMap = dbRacks.stream().collect(Collectors.toMap(WarehouseRack::getId, r -> r));
        Map<UUID, WarehouseBin> dbBinMap = dbBins.stream().collect(Collectors.toMap(WarehouseBin::getId, b -> b));

        Set<UUID> reqRackIds = new HashSet<>();
        Set<UUID> reqBinIds = new HashSet<>();

        if (request.getRacks() != null) {
            for (RackSaveRequest rReq : request.getRacks()) {
                if (rReq.getId() != null) reqRackIds.add(rReq.getId());
                if (rReq.getBins() != null) {
                    for (BinSaveRequest bReq : rReq.getBins()) {
                        if (bReq.getId() != null) reqBinIds.add(bReq.getId());
                    }
                }
            }
        }

        // ==================== Ràng buộc đối với TENANT ROLE ====================
        if (isTenantRole) {
            // 1. Chặn thêm mới (bản ghi không có ID trong request)
            if (request.getRacks() != null) {
                for (RackSaveRequest rReq : request.getRacks()) {
                    if (rReq.getId() == null || !dbRackMap.containsKey(rReq.getId())) {
                        throw new BadRequestException("Tenant không được phép thêm kệ hàng mới vào sơ đồ.");
                    }
                    if (rReq.getBins() != null) {
                        for (BinSaveRequest bReq : rReq.getBins()) {
                            if (bReq.getId() == null || !dbBinMap.containsKey(bReq.getId())) {
                                throw new BadRequestException("Tenant không được phép thêm ô chứa mới vào sơ đồ.");
                            }
                        }
                    }
                }
            }

            // 2. Chặn xóa (số lượng bản ghi trong DB phải khớp với request)
            if (reqRackIds.size() != dbRacks.size() || reqBinIds.size() != dbBins.size()) {
                throw new BadRequestException("Tenant không được phép xóa kệ hàng hoặc ô chứa khỏi sơ đồ.");
            }
        }

        // ==================== Delete Handling (Dành riêng cho Owner) ====================
        if (!isTenantRole) {
            List<UUID> racksToDelete = dbRacks.stream().map(WarehouseRack::getId)
                    .filter(id -> !reqRackIds.contains(id)).collect(Collectors.toList());
            List<UUID> binsToDelete = dbBins.stream().map(WarehouseBin::getId)
                    .filter(id -> !reqBinIds.contains(id)).collect(Collectors.toList());

            // Inventory Guard: Không cho xóa ô/kệ đang có tồn kho
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

            for (UUID binId : binsToDelete) {
                binRepository.deleteById(binId);
            }
            for (UUID rackId : racksToDelete) {
                rackRepository.deleteById(rackId);
            }
        }

        // ==================== Perform Save & Update ====================
        if (request.getRacks() != null) {
            for (RackSaveRequest rReq : request.getRacks()) {
                // Ràng buộc biên: Rack trong Layout
                if (rReq.getCoordinateX() < 0 || rReq.getCoordinateY() < 0 ||
                        rReq.getCoordinateX() + (rReq.getWidth() != null ? rReq.getWidth() : 0) > layout.getWidth() ||
                        rReq.getCoordinateY() + (rReq.getLength() != null ? rReq.getLength() : 0) > layout.getLength()) {
                    throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES,
                            "Tọa độ/Kích thước Rack " + rReq.getName() + " vượt quá biên giới hạn của Layout (" + layout.getWidth() + "x" + layout.getLength() + ")");
                }

                WarehouseRack rack;
                if (rReq.getId() != null && dbRackMap.containsKey(rReq.getId())) {
                    rack = dbRackMap.get(rReq.getId());
                    if (isTenantRole) {
                        // Tenant CHỈ ĐƯỢC PHÉP CẬP NHẬT TỌA ĐỘ VÀ GÓC XOAY
                        rack.setCoordinateX(rReq.getCoordinateX());
                        rack.setCoordinateY(rReq.getCoordinateY());
                        if (rReq.getPositionZ() != null) rack.setPositionZ(rReq.getPositionZ());
                        if (rReq.getRotation() != null) rack.setRotation(rReq.getRotation());
                    } else {
                        // Owner cập nhật đầy đủ
                        rack.setZoneName(rReq.getZoneName());
                        rack.setZoneCode(rReq.getZoneCode());
                        rack.setName(rReq.getName());
                        rack.setCode(rReq.getCode());
                        rack.setMaxWeight(rReq.getMaxWeight());
                        rack.setMaxVolume(rReq.getMaxVolume());
                        rack.setCoordinateX(rReq.getCoordinateX());
                        rack.setCoordinateY(rReq.getCoordinateY());
                        if (rReq.getPositionZ() != null) rack.setPositionZ(rReq.getPositionZ());
                        if (rReq.getRotation() != null) rack.setRotation(rReq.getRotation());
                        rack.setWidth(rReq.getWidth());
                        if (rReq.getLength() != null) rack.setLength(rReq.getLength());
                        rack.setHeight(rReq.getHeight());
                    }
                } else {
                    rack = WarehouseRack.builder()
                            .layout(layout)
                            .zoneName(rReq.getZoneName())
                            .zoneCode(rReq.getZoneCode())
                            .name(rReq.getName())
                            .code(rReq.getCode())
                            .maxWeight(rReq.getMaxWeight())
                            .maxVolume(rReq.getMaxVolume())
                            .coordinateX(rReq.getCoordinateX())
                            .coordinateY(rReq.getCoordinateY())
                            .positionZ(rReq.getPositionZ() != null ? rReq.getPositionZ() : 0)
                            .rotation(rReq.getRotation() != null ? rReq.getRotation() : 0)
                            .width(rReq.getWidth())
                            .length(rReq.getLength() != null ? rReq.getLength() : 1)
                            .height(rReq.getHeight())
                            .build();
                }
                rack = rackRepository.save(rack);

                if (rReq.getBins() != null) {
                    for (BinSaveRequest bReq : rReq.getBins()) {
                        WarehouseBin bin;
                        if (bReq.getId() != null && dbBinMap.containsKey(bReq.getId())) {
                            bin = dbBinMap.get(bReq.getId());
                            if (isTenantRole) {
                                bin.setCoordinateX(bReq.getCoordinateX());
                                bin.setCoordinateY(bReq.getCoordinateY());
                                if (bReq.getPositionZ() != null) bin.setPositionZ(bReq.getPositionZ());
                            } else {
                                bin.setRack(rack);
                                bin.setName(bReq.getName());
                                bin.setCode(bReq.getCode());
                                bin.setMaxWeight(bReq.getMaxWeight());
                                bin.setMaxVolume(bReq.getMaxVolume());
                                if (bReq.getShelfLevel() != null) bin.setShelfLevel(bReq.getShelfLevel());
                                bin.setCoordinateX(bReq.getCoordinateX());
                                bin.setCoordinateY(bReq.getCoordinateY());
                                if (bReq.getPositionZ() != null) bin.setPositionZ(bReq.getPositionZ());
                                bin.setWidth(bReq.getWidth());
                                if (bReq.getLength() != null) bin.setLength(bReq.getLength());
                                bin.setHeight(bReq.getHeight());
                            }
                        } else {
                            bin = WarehouseBin.builder()
                                    .rack(rack)
                                    .name(bReq.getName())
                                    .code(bReq.getCode())
                                    .maxWeight(bReq.getMaxWeight())
                                    .maxVolume(bReq.getMaxVolume())
                                    .shelfLevel(bReq.getShelfLevel() != null ? bReq.getShelfLevel() : 1)
                                    .coordinateX(bReq.getCoordinateX())
                                    .coordinateY(bReq.getCoordinateY())
                                    .positionZ(bReq.getPositionZ() != null ? bReq.getPositionZ() : 0)
                                    .width(bReq.getWidth())
                                    .length(bReq.getLength() != null ? bReq.getLength() : 1)
                                    .height(bReq.getHeight())
                                    .build();
                        }
                        binRepository.save(bin);
                    }
                }
            }
        }

        return mapToLayoutResponse(layout);
    }

    /**
     * Helper ánh xạ đệ quy từ Entity sang Response DTO (2D/3D + Thống kê ô chứa)
     */
    private WarehouseLayoutResponse mapToLayoutResponse(WarehouseLayout layout) {
        List<WarehouseRack> racks = rackRepository.findAllByLayoutId(layout.getId());
        List<WarehouseBin> bins = binRepository.findAllByRackLayoutId(layout.getId());

        Map<UUID, List<WarehouseBin>> binsByRack = bins.stream()
                .collect(Collectors.groupingBy(b -> b.getRack().getId()));

        int totalRacks = racks.size();
        int totalBins = bins.size();
        int occupiedBins = 0;

        List<RackResponse> rackResponses = new ArrayList<>();

        for (WarehouseRack rack : racks) {
            List<WarehouseBin> rackBins = binsByRack.getOrDefault(rack.getId(), Collections.emptyList());
            List<WarehouseBinResponse> binResponses = new ArrayList<>();

            for (WarehouseBin bin : rackBins) {
                boolean isOccupied = stockBatchRepository.existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(bin.getId(), 0);
                if (isOccupied) {
                    occupiedBins++;
                }

                binResponses.add(WarehouseBinResponse.builder()
                        .id(bin.getId())
                        .rackId(rack.getId())
                        .name(bin.getName())
                        .code(bin.getCode())
                        .maxWeight(bin.getMaxWeight())
                        .maxVolume(bin.getMaxVolume())
                        .shelfLevel(bin.getShelfLevel())
                        .coordinateX(bin.getCoordinateX())
                        .coordinateY(bin.getCoordinateY())
                        .positionZ(bin.getPositionZ())
                        .width(bin.getWidth())
                        .length(bin.getLength())
                        .height(bin.getHeight())
                        .isOccupied(isOccupied)
                        .build());
            }

            rackResponses.add(RackResponse.builder()
                    .id(rack.getId())
                    .layoutId(layout.getId())
                    .zoneName(rack.getZoneName())
                    .zoneCode(rack.getZoneCode())
                    .name(rack.getName())
                    .code(rack.getCode())
                    .maxWeight(rack.getMaxWeight())
                    .maxVolume(rack.getMaxVolume())
                    .coordinateX(rack.getCoordinateX())
                    .coordinateY(rack.getCoordinateY())
                    .positionZ(rack.getPositionZ())
                    .rotation(rack.getRotation())
                    .width(rack.getWidth())
                    .length(rack.getLength())
                    .height(rack.getHeight())
                    .bins(binResponses)
                    .build());
        }

        int emptyBins = totalBins - occupiedBins;

        return WarehouseLayoutResponse.builder()
                .id(layout.getId())
                .warehouseId(layout.getWarehouse().getId())
                .tenantId(layout.getTenant() != null ? layout.getTenant().getId() : null)
                .isDefault(layout.isDefault())
                .width(layout.getWidth())
                .length(layout.getLength())
                .height(layout.getHeight())
                .totalRacks(totalRacks)
                .totalBins(totalBins)
                .occupiedBins(occupiedBins)
                .emptyBins(emptyBins)
                .racks(rackResponses)
                .build();
    }
}
