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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;





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
    private final ObjectMapper objectMapper;




    @Transactional(readOnly = true)
    public WarehouseLayoutResponse getLayoutTree(UUID warehouseId, UUID userId, String role) {
        log.info("Fetching layout tree for warehouse: {}, user: {}, role: {}", warehouseId, userId, role);

        WarehouseLayout layout = null;

        if ("OWNER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            if (layout != null && "OWNER".equalsIgnoreCase(role)
                    && (userId == null || layout.getWarehouse().getOwner() == null
                    || !layout.getWarehouse().getOwner().getId().equals(userId))) {
                throw new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED);
            }
        } else if ("TENANT".equalsIgnoreCase(role)) {
            if (userId == null
                    || !contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(userId, warehouseId)) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }

            layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId).orElse(null);
            if (layout == null) {

                layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            }
        } else {

            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
        }

        if (layout == null) {
            throw new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND);
        }

        return mapToLayoutResponse(layout);
    }




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
                .positions(defaultLayout.getPositions())
                .build();
        tenantLayout = layoutRepository.save(tenantLayout);


        List<WarehouseRack> defaultRacks = rackRepository.findAllByLayoutId(defaultLayout.getId());
        List<WarehouseBin> defaultBins = binRepository.findAllByRackLayoutId(defaultLayout.getId());

        Map<UUID, List<WarehouseBin>> binsByRack = defaultBins.stream()
                .collect(Collectors.groupingBy(b -> b.getRack().getId()));

        for (WarehouseRack rack : defaultRacks) {
            WarehouseRack cloneRack = WarehouseRack.builder()
                    .layout(tenantLayout)
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
            if (warehouse.getStatus() == fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.RENTED) {
                throw new BadRequestException("Không thể chỉnh sửa sơ đồ layout kho trong thời gian kho đang được cho thuê.");
            }

            layout = layoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId).orElse(null);
            if (layout == null) {
                layout = WarehouseLayout.builder()
                        .warehouse(warehouse)
                        .isDefault(true)
                        .width(request.getWidth())
                        .length(request.getLength() != null ? request.getLength() : new BigDecimal("100"))
                        .height(request.getHeight())
                        .build();
            } else {
                layout.setWidth(request.getWidth());
                if (request.getLength() != null) layout.setLength(request.getLength());
                layout.setHeight(request.getHeight());
            }
            layout.setPositions(serializePositions(request.getPositions()));
            WarehouseLayout savedOwnerLayout = layoutRepository.save(layout);
            if (savedOwnerLayout != null) layout = savedOwnerLayout;

        } else if (isTenantRole) {
            boolean hasActiveContract = contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(userId, warehouseId);
            if (!hasActiveContract) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }

            layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId).orElse(null);
            if (layout == null) {
                cloneLayout(warehouseId, userId);
                layout = layoutRepository.findByWarehouseIdAndTenantId(warehouseId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));
            }
            layout.setPositions(serializePositions(request.getPositions()));
            WarehouseLayout savedTenantLayout = layoutRepository.save(layout);
            if (savedTenantLayout != null) layout = savedTenantLayout;

        } else {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }


        List<WarehouseRack> dbRacks = rackRepository.findAllByLayoutId(layout.getId());
        List<WarehouseBin> dbBins = binRepository.findAllByRackLayoutId(layout.getId());

        Map<UUID, WarehouseRack> dbRackMap = dbRacks.stream().collect(Collectors.toMap(WarehouseRack::getId, r -> r));
        Map<UUID, WarehouseBin> dbBinMap = dbBins.stream().collect(Collectors.toMap(WarehouseBin::getId, b -> b));

        validateRequestGeometry(layout, request, isTenantRole);

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


        if (isTenantRole) {

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


            if (reqRackIds.size() != dbRacks.size() || reqBinIds.size() != dbBins.size()) {
                throw new BadRequestException("Tenant không được phép xóa kệ hàng hoặc ô chứa khỏi sơ đồ.");
            }
        }


        if (!isTenantRole) {
            List<UUID> racksToDelete = dbRacks.stream().map(WarehouseRack::getId)
                    .filter(id -> !reqRackIds.contains(id)).collect(Collectors.toList());
            List<UUID> binsToDelete = dbBins.stream().map(WarehouseBin::getId)
                    .filter(id -> !reqBinIds.contains(id)).collect(Collectors.toList());


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


        if (request.getRacks() != null) {
            for (RackSaveRequest rReq : request.getRacks()) {
                WarehouseRack rack;
                if (rReq.getId() != null && dbRackMap.containsKey(rReq.getId())) {
                    rack = dbRackMap.get(rReq.getId());
                    if (isTenantRole) {

                        rack.setCoordinateX(rReq.getCoordinateX());
                        rack.setCoordinateY(rReq.getCoordinateY());
                        if (rReq.getPositionZ() != null) rack.setPositionZ(rReq.getPositionZ());
                        if (rReq.getRotation() != null) rack.setRotation(rReq.getRotation());
                    } else {

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
                            .name(rReq.getName())
                            .code(rReq.getCode())
                            .maxWeight(rReq.getMaxWeight())
                            .maxVolume(rReq.getMaxVolume())
                            .coordinateX(rReq.getCoordinateX())
                            .coordinateY(rReq.getCoordinateY())
                            .positionZ(rReq.getPositionZ() != null ? rReq.getPositionZ() : BigDecimal.ZERO)
                            .rotation(rReq.getRotation() != null ? rReq.getRotation() : 0)
                            .width(rReq.getWidth())
                            .length(rReq.getLength() != null ? rReq.getLength() : BigDecimal.ONE)
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
                                    .positionZ(bReq.getPositionZ() != null ? bReq.getPositionZ() : BigDecimal.ZERO)
                                    .width(bReq.getWidth())
                                    .length(bReq.getLength() != null ? bReq.getLength() : BigDecimal.ONE)
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

                List<String> binOccupiedPositions = calculateOccupiedPositions(
                        bin.getCoordinateX(), bin.getCoordinateY(), bin.getWidth(), bin.getLength());

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
                        .occupiedPositions(binOccupiedPositions)
                        .build());
            }

            List<String> rackOccupiedPositions = calculateOccupiedPositions(
                    rack.getCoordinateX(), rack.getCoordinateY(), rack.getWidth(), rack.getLength());

            rackResponses.add(RackResponse.builder()
                    .id(rack.getId())
                    .layoutId(layout.getId())
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
                    .occupiedPositions(rackOccupiedPositions)
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
                .positions(deserializePositions(layout.getPositions()))
                .build();
    }


    private String serializePositions(List<String> positions) {
        if (positions == null || positions.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(positions);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize positions: {}", e.getMessage());
            return null;
        }
    }


    private List<String> deserializePositions(String positionsJson) {
        if (positionsJson == null || positionsJson.isBlank()) return null;
        try {
            return objectMapper.readValue(positionsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize positions: {}", e.getMessage());
            return null;
        }
    }

    private void validateRequestGeometry(WarehouseLayout layout, BulkLayoutSaveRequest request, boolean tenantRole) {
        requirePositive("layout.width", request.getWidth());
        requirePositive("layout.length", request.getLength());
        requirePositive("layout.height", request.getHeight());

        BigDecimal layoutWidth = tenantRole && layout.getWidth() != null ? layout.getWidth() : request.getWidth();
        BigDecimal layoutLength = tenantRole && layout.getLength() != null ? layout.getLength() : request.getLength();
        BigDecimal layoutHeight = tenantRole && layout.getHeight() != null ? layout.getHeight() : request.getHeight();
        requirePositive("stored layout.width", layoutWidth);
        requirePositive("stored layout.length", layoutLength);
        requirePositive("stored layout.height", layoutHeight);
        List<RackSaveRequest> racks = request.getRacks() == null ? Collections.emptyList() : request.getRacks();
        Set<String> rackCodes = new HashSet<>();

        for (int i = 0; i < racks.size(); i++) {
            RackSaveRequest rack = racks.get(i);
            if (rack.getName() == null || rack.getName().isBlank()
                    || rack.getCode() == null || rack.getCode().isBlank()) {
                throw invalidGeometry("Rack name and code are required");
            }
            if (!rackCodes.add(rack.getCode().trim().toLowerCase(Locale.ROOT))) {
                throw invalidGeometry("Rack code must be unique within a layout: " + rack.getCode());
            }
            validateCapacity("rack", rack.getName(), rack.getMaxWeight(), rack.getMaxVolume());
            validateRotation(rack.getRotation());
            requireNonNegative("rack.coordinateX", rack.getCoordinateX());
            requireNonNegative("rack.coordinateY", rack.getCoordinateY());
            requireNonNegative("rack.positionZ", rack.getPositionZ() == null ? BigDecimal.ZERO : rack.getPositionZ());
            requirePositive("rack.width", rack.getWidth());
            requirePositive("rack.length", rack.getLength());
            requirePositive("rack.height", rack.getHeight());

            BigDecimal rackWidth = effectiveWidth(rack.getWidth(), rack.getLength(), rack.getRotation());
            BigDecimal rackLength = effectiveLength(rack.getWidth(), rack.getLength(), rack.getRotation());
            BigDecimal rackX = rack.getCoordinateX();
            BigDecimal rackY = rack.getCoordinateY();
            BigDecimal rackZ = rack.getPositionZ() == null ? BigDecimal.ZERO : rack.getPositionZ();
            ensureInside("Rack " + rack.getName(), rackX, rackY, rackZ,
                    rackWidth, rackLength, rack.getHeight(), layoutWidth, layoutLength, layoutHeight);
            validateGeometricCapacity("rack", rack.getName(), rackWidth, rackLength, rack.getHeight(), rack.getMaxVolume());

            for (int previousIndex = 0; previousIndex < i; previousIndex++) {
                RackSaveRequest previous = racks.get(previousIndex);
                if (overlaps(
                        rackX, rackY, rackZ, rackWidth, rackLength, rack.getHeight(),
                        previous.getCoordinateX(), previous.getCoordinateY(),
                        previous.getPositionZ() == null ? BigDecimal.ZERO : previous.getPositionZ(),
                        effectiveWidth(previous.getWidth(), previous.getLength(), previous.getRotation()),
                        effectiveLength(previous.getWidth(), previous.getLength(), previous.getRotation()),
                        previous.getHeight())) {
                    throw invalidGeometry("Rack " + rack.getName() + " overlaps rack " + previous.getName());
                }
            }

            List<BinSaveRequest> bins = rack.getBins() == null ? Collections.emptyList() : rack.getBins();
            Set<String> binCodes = new HashSet<>();
            for (int binIndex = 0; binIndex < bins.size(); binIndex++) {
                BinSaveRequest bin = bins.get(binIndex);
                if (bin.getName() == null || bin.getName().isBlank()
                        || bin.getCode() == null || bin.getCode().isBlank()) {
                    throw invalidGeometry("Bin name and code are required");
                }
                if (!binCodes.add(bin.getCode().trim().toLowerCase(Locale.ROOT))) {
                    throw invalidGeometry("Bin code must be unique within rack " + rack.getName()
                            + ": " + bin.getCode());
                }
                validateCapacity("bin", bin.getName(), bin.getMaxWeight(), bin.getMaxVolume());
                requireNonNegative("bin.coordinateX", bin.getCoordinateX());
                requireNonNegative("bin.coordinateY", bin.getCoordinateY());
                requireNonNegative("bin.positionZ", bin.getPositionZ() == null ? BigDecimal.ZERO : bin.getPositionZ());
                requirePositive("bin.width", bin.getWidth());
                requirePositive("bin.length", bin.getLength());
                requirePositive("bin.height", bin.getHeight());

                BigDecimal binZ = bin.getPositionZ() == null ? BigDecimal.ZERO : bin.getPositionZ();
                ensureInside("Bin " + bin.getName() + " in rack " + rack.getName(),
                        bin.getCoordinateX(), bin.getCoordinateY(), binZ,
                        bin.getWidth(), bin.getLength(), bin.getHeight(),
                        rackWidth, rackLength, rack.getHeight());
                validateGeometricCapacity("bin", bin.getName(), bin.getWidth(), bin.getLength(), bin.getHeight(), bin.getMaxVolume());

                for (int previousIndex = 0; previousIndex < binIndex; previousIndex++) {
                    BinSaveRequest previous = bins.get(previousIndex);
                    if (overlaps(
                            bin.getCoordinateX(), bin.getCoordinateY(), binZ,
                            bin.getWidth(), bin.getLength(), bin.getHeight(),
                            previous.getCoordinateX(), previous.getCoordinateY(),
                            previous.getPositionZ() == null ? BigDecimal.ZERO : previous.getPositionZ(),
                            previous.getWidth(), previous.getLength(), previous.getHeight())) {
                        throw invalidGeometry("Bin " + bin.getName() + " overlaps bin " + previous.getName()
                                + " in rack " + rack.getName());
                    }
                }
            }
        }
    }

    private void validateCapacity(String type, String name, BigDecimal maxWeight, BigDecimal maxVolume) {
        if (maxWeight != null && maxWeight.signum() < 0) {
            throw invalidGeometry(type + " " + name + " maxWeight cannot be negative");
        }
        if (maxVolume != null && maxVolume.signum() < 0) {
            throw invalidGeometry(type + " " + name + " maxVolume cannot be negative");
        }
    }

    private void validateGeometricCapacity(String type, String name, BigDecimal width, BigDecimal length,
                                            BigDecimal height, BigDecimal maxVolume) {
        if (maxVolume != null && maxVolume.signum() > 0
                && maxVolume.compareTo(width.multiply(length).multiply(height)) > 0) {
            throw invalidGeometry(type + " " + name + " maxVolume cannot exceed its geometric volume");
        }
    }

    private void ensureInside(String name, BigDecimal x, BigDecimal y, BigDecimal z,
                              BigDecimal width, BigDecimal length, BigDecimal height,
                              BigDecimal parentWidth, BigDecimal parentLength, BigDecimal parentHeight) {
        if (x.add(width).compareTo(parentWidth) > 0
                || y.add(length).compareTo(parentLength) > 0
                || z.add(height).compareTo(parentHeight) > 0) {
            throw invalidGeometry(name + " exceeds its parent bounds");
        }
    }

    private boolean overlaps(BigDecimal x1, BigDecimal y1, BigDecimal z1,
                             BigDecimal width1, BigDecimal length1, BigDecimal height1,
                             BigDecimal x2, BigDecimal y2, BigDecimal z2,
                             BigDecimal width2, BigDecimal length2, BigDecimal height2) {
        return x1.compareTo(x2.add(width2)) < 0 && x1.add(width1).compareTo(x2) > 0
                && y1.compareTo(y2.add(length2)) < 0 && y1.add(length1).compareTo(y2) > 0
                && z1.compareTo(z2.add(height2)) < 0 && z1.add(height1).compareTo(z2) > 0;
    }

    private BigDecimal effectiveWidth(BigDecimal width, BigDecimal length, Integer rotation) {
        return isQuarterTurn(rotation) ? length : width;
    }

    private BigDecimal effectiveLength(BigDecimal width, BigDecimal length, Integer rotation) {
        return isQuarterTurn(rotation) ? width : length;
    }

    private boolean isQuarterTurn(Integer rotation) {
        return rotation != null && (Math.floorMod(rotation, 180) == 90);
    }

    private void validateRotation(Integer rotation) {
        if (rotation != null && (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)) {
            throw invalidGeometry("rotation must be one of 0, 90, 180, 270 degrees");
        }
    }

    private void requirePositive(String field, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw invalidGeometry(field + " must be greater than 0 meters");
        }
    }

    private void requireNonNegative(String field, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw invalidGeometry(field + " must be non-negative meters");
        }
    }

    private BadRequestException invalidGeometry(String message) {
        return new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES, message);
    }

    private List<String> calculateOccupiedPositions(BigDecimal startX, BigDecimal startY,
                                                     BigDecimal width, BigDecimal length) {
        List<String> positions = new java.util.ArrayList<>();
        if (startX == null || startY == null) return positions;
        int startCellX = startX.setScale(0, RoundingMode.FLOOR).intValue();
        int startCellY = startY.setScale(0, RoundingMode.FLOOR).intValue();
        int endCellX = startX.add(width == null ? BigDecimal.ONE : width)
                .setScale(0, RoundingMode.CEILING).intValue();
        int endCellY = startY.add(length == null ? BigDecimal.ONE : length)
                .setScale(0, RoundingMode.CEILING).intValue();

        for (int x = startCellX; x < endCellX; x++) {
            for (int y = startCellY; y < endCellY; y++) {
                positions.add(x + ":" + y);
            }
        }
        return positions;
    }
}
