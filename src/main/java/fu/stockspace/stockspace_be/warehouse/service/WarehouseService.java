package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.*;
import fu.stockspace.stockspace_be.warehouse.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý nghiệp vụ Warehouse.
 *
 * Chức năng:
 * - Owner: tạo / sửa / xoá / cập nhật trạng thái / thêm ảnh / xem kho của mình
 * - Public/Tenant: tìm kiếm & xem chi tiết kho (chỉ kho đã verified)
 * - Admin (internal): verify / reject listing, xem tất cả kho
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final int MAX_IMAGES_PER_WAREHOUSE = 10;

    private final WarehouseRepository warehouseRepository;
    private final WarehouseTypeRepository warehouseTypeRepository;
    private final WarehouseImageRepository warehouseImageRepository;
    private final UserRepository userRepository;

    // ==================== Owner: CRUD ====================

    /**
     * Owner tạo mới Warehouse.
     * Kho sẽ ở trạng thái PENDING_VERIFICATION cho đến khi được Admin/Inspector duyệt.
     */
    @Transactional
    public WarehouseResponse createWarehouse(Long ownerId, CreateWarehouseRequest request) {
        log.info("Owner {} creating new warehouse: {}", ownerId, request.getName());

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        WarehouseType type = warehouseTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));

        Warehouse warehouse = Warehouse.builder()
                .owner(owner)
                .type(type)
                .name(request.getName())
                .address(request.getAddress())
                .description(request.getDescription())
                .capacity(request.getCapacity())
                .pricePerMonth(request.getPricePerMonth())
                .status(WarehouseStatus.PENDING_VERIFICATION)
                .isVerified(false)
                .build();

        warehouse = warehouseRepository.save(warehouse);

        // Thêm ảnh nếu có
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            attachImages(warehouse, request.getImageUrls());
        }

        log.info("Warehouse created: {} (ID: {})", warehouse.getName(), warehouse.getId());
        return mapToResponse(warehouse);
    }

    /**
     * Owner cập nhật thông tin Warehouse.
     */
    @Transactional
    public WarehouseResponse updateWarehouse(Long ownerId, Long warehouseId, UpdateWarehouseRequest request) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        if (StringUtils.hasText(request.getName())) {
            warehouse.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getAddress())) {
            warehouse.setAddress(request.getAddress().trim());
        }
        if (request.getDescription() != null) {
            warehouse.setDescription(request.getDescription().trim());
        }
        if (request.getCapacity() != null) {
            warehouse.setCapacity(request.getCapacity());
        }
        if (request.getPricePerMonth() != null) {
            warehouse.setPricePerMonth(request.getPricePerMonth());
        }
        if (request.getTypeId() != null) {
            WarehouseType type = warehouseTypeRepository.findById(request.getTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));
            warehouse.setType(type);
        }

        warehouse = warehouseRepository.save(warehouse);
        log.info("Owner {} updated warehouse {}", ownerId, warehouseId);
        return mapToResponse(warehouse);
    }

    /**
     * Owner xoá Warehouse.
     * Ràng buộc: chỉ xoá được khi không có tenant đang thuê (status != RENTED).
     */
    @Transactional
    public void deleteWarehouse(Long ownerId, Long warehouseId) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        if (warehouse.getStatus() == WarehouseStatus.RENTED) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_CANNOT_DELETE_RENTED);
        }

        warehouse.setDeleted(true);
        warehouseRepository.save(warehouse);
        log.info("Owner {} deleted warehouse {}", ownerId, warehouseId);
    }

    /**
     * Owner cập nhật trạng thái Warehouse (AVAILABLE ↔ INACTIVE).
     * Không cho phép tự set RENTED hoặc PENDING_VERIFICATION qua API này.
     */
    @Transactional
    public WarehouseResponse updateStatus(Long ownerId, Long warehouseId, WarehouseStatus newStatus) {
        if (newStatus == WarehouseStatus.RENTED || newStatus == WarehouseStatus.PENDING_VERIFICATION) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }

        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);
        warehouse.setStatus(newStatus);
        warehouse = warehouseRepository.save(warehouse);

        log.info("Owner {} updated warehouse {} status to {}", ownerId, warehouseId, newStatus);
        return mapToResponse(warehouse);
    }

    /**
     * Owner xem danh sách kho của mình (phân trang).
     */
    @Transactional(readOnly = true)
    public PagedWarehouseResponse getMyWarehouses(Long ownerId, int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Warehouse> warehousePage = warehouseRepository.findByOwnerId(ownerId, pageable);
        return toPagedResponse(warehousePage);
    }

    // ==================== Owner: Images ====================

    /**
     * Owner thêm ảnh vào Warehouse.
     * Giới hạn tối đa MAX_IMAGES_PER_WAREHOUSE ảnh.
     */
    @Transactional
    public List<String> addImages(Long ownerId, Long warehouseId, List<String> imageUrls) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        int currentCount = warehouseImageRepository.countByWarehouseId(warehouseId);
        if (currentCount + imageUrls.size() > MAX_IMAGES_PER_WAREHOUSE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_IMAGE_LIMIT_EXCEEDED);
        }

        List<String> saved = attachImages(warehouse, imageUrls);
        log.info("Added {} images to warehouse {}", imageUrls.size(), warehouseId);
        return saved;
    }

    /**
     * Owner xóa tất cả ảnh và thay bằng danh sách mới.
     */
    @Transactional
    public List<String> replaceImages(Long ownerId, Long warehouseId, List<String> imageUrls) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        warehouseImageRepository.deleteAllByWarehouseId(warehouseId);
        warehouse.getImages().clear();

        List<String> saved = attachImages(warehouse, imageUrls);
        log.info("Replaced images for warehouse {}", warehouseId);
        return saved;
    }

    // ==================== Public / Tenant: Search & Detail ====================

    /**
     * Tìm kiếm kho công khai — chỉ trả về kho đã verified.
     */
    @Transactional(readOnly = true)
    public PagedWarehouseResponse searchWarehouses(WarehouseSearchRequest request,
                                                    int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        String kw = StringUtils.hasText(request.getKeyword()) ? "%" + request.getKeyword().trim().toLowerCase() + "%" : null;

        Page<Warehouse> result = warehouseRepository.searchPublic(
                kw,
                request.getStatus(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getMinCapacity(),
                pageable
        );

        return toPagedResponse(result);
    }

    /**
     * Xem chi tiết một Warehouse theo ID.
     * Public: chỉ xem được kho đã verified.
     */
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseDetail(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (!warehouse.isVerified()) {
            throw new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        return mapToResponse(warehouse);
    }

    // ==================== Admin (internal) ====================

    /**
     * Admin duyệt Warehouse listing — set isVerified = true + status = AVAILABLE.
     * Gọi từ AdminWarehouseService.
     */
    @Transactional
    public WarehouseResponse verifyWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (warehouse.isVerified()) {
            throw new ResourceConflictException(ErrorCode.WAREHOUSE_ALREADY_VERIFIED);
        }

        warehouse.setVerified(true);
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse = warehouseRepository.save(warehouse);

        log.info("Admin verified warehouse {}", warehouseId);
        return mapToResponse(warehouse);
    }

    /**
     * Admin từ chối Warehouse listing — set status = INACTIVE.
     */
    @Transactional
    public WarehouseResponse rejectWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setStatus(WarehouseStatus.INACTIVE);
        warehouse = warehouseRepository.save(warehouse);

        log.info("Admin rejected warehouse listing {}", warehouseId);
        return mapToResponse(warehouse);
    }

    /**
     * Admin / Inspector xem tất cả kho (không lọc verified).
     */
    @Transactional(readOnly = true)
    public PagedWarehouseResponse getAllWarehouses(WarehouseSearchRequest request,
                                                   int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        String kw = StringUtils.hasText(request.getKeyword()) ? "%" + request.getKeyword().trim().toLowerCase() + "%" : null;

        Page<Warehouse> result = warehouseRepository.searchAll(
                kw,
                request.getStatus(),
                request.getIsVerified(),
                pageable
        );

        return toPagedResponse(result);
    }

    /**
     * Inspector set isVerified = true khi kiểm định PASSED.
     * Gọi từ InspectionService.
     */
    @Transactional
    public void markAsVerifiedByInspection(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setVerified(true);
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouseRepository.save(warehouse);
        log.info("Warehouse {} verified via inspection", warehouseId);
    }

    /**
     * Cập nhật status kho về AVAILABLE sau khi hợp đồng kết thúc.
     * Gọi từ ContractService.
     */
    @Transactional
    public void markAsAvailable(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouseRepository.save(warehouse);
        log.info("Warehouse {} marked as AVAILABLE", warehouseId);
    }

    /**
     * Set warehouse status = RENTED sau khi booking được approve.
     * Gọi từ BookingService.
     */
    @Transactional
    public void markAsRented(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setStatus(WarehouseStatus.RENTED);
        warehouseRepository.save(warehouse);
        log.info("Warehouse {} marked as RENTED", warehouseId);
    }

    // ==================== Private helpers ====================

    private Warehouse getOwnedWarehouse(Long ownerId, Long warehouseId) {
        return warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED));
    }

    private List<String> attachImages(Warehouse warehouse, List<String> imageUrls) {
        List<String> savedUrls = new ArrayList<>();
        int startOrder = warehouse.getImages().size();

        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (!StringUtils.hasText(url)) continue;

            WarehouseImage image = WarehouseImage.builder()
                    .warehouse(warehouse)
                    .imageUrl(url.trim())
                    .displayOrder(startOrder + i)
                    .build();

            warehouseImageRepository.save(image);
            warehouse.getImages().add(image);
            savedUrls.add(url.trim());
        }

        return savedUrls;
    }

    private WarehouseResponse mapToResponse(Warehouse w) {
        List<String> urls = w.getImages().stream()
                .map(WarehouseImage::getImageUrl)
                .collect(Collectors.toList());

        String cover = urls.isEmpty() ? null : urls.get(0);

        return WarehouseResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .address(w.getAddress())
                .description(w.getDescription())
                .capacity(w.getCapacity())
                .pricePerMonth(w.getPricePerMonth())
                .status(w.getStatus().name())
                .isVerified(w.isVerified())
                .typeId(w.getType() != null ? w.getType().getId() : null)
                .typeName(w.getType() != null ? w.getType().getName() : null)
                .ownerId(w.getOwner() != null ? w.getOwner().getId() : null)
                .ownerName(w.getOwner() != null ? w.getOwner().getFullName() : null)
                .ownerPhone(w.getOwner() != null ? w.getOwner().getPhone() : null)
                .coverImageUrl(cover)
                .imageUrls(urls)
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private PagedWarehouseResponse toPagedResponse(Page<Warehouse> page) {
        List<WarehouseResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedWarehouseResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
