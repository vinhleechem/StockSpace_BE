package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.*;
import fu.stockspace.stockspace_be.warehouse.repository.*;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import fu.stockspace.stockspace_be.common.repository.SystemPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import fu.stockspace.stockspace_be.notification.service.NotificationService;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;









@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final int MAX_IMAGES_PER_WAREHOUSE = 10;
    private static final String PUBLICATION_DRAFT = "DRAFT";
    private static final String PUBLICATION_PUBLISHED = "PUBLISHED";
    private static final String PUBLICATION_EXPIRED = "EXPIRED";

    private final WarehouseRepository warehouseRepository;
    private final WarehouseTypeRepository warehouseTypeRepository;
    private final WarehouseImageRepository warehouseImageRepository;
    private final UserRepository userRepository;
    private final SystemPolicyRepository systemPolicyRepository;
    private final NotificationService notificationService;
    private final TenantWarehouseAccessService tenantWarehouseAccessService;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getActiveContractWarehouses(UUID tenantId) {
        List<Warehouse> warehouses = tenantWarehouseAccessService.findActiveContractWarehouses(tenantId);


        boolean isStaff = SecurityUtil.getCurrentUser()
                .map(user -> user.getRoles().stream()
                        .anyMatch(r -> r.getName().equals("ROLE_STAFF")))
                .orElse(false);

        if (isStaff) {
            UUID currentUserId = SecurityUtil.getCurrentUserId();
            warehouses = tenantWarehouseAccessService
                    .findAccessibleContractWarehouses(tenantId, currentUserId);
        }

        return warehouses.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }









    @Transactional
    public WarehouseResponse createWarehouse(UUID ownerId, CreateWarehouseRequest request) {
        log.info("Owner {} creating new warehouse: {}", ownerId, request.getName());

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        WarehouseType type = warehouseTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));

        RentalPricingType pricingType = resolvePricingType(request.getRentalPricingType());
        java.math.BigDecimal rentalPrice = request.getRentalPrice();
        validateRentalPricing(pricingType, rentalPrice);

        SystemPolicy policy = systemPolicyRepository.findFirstByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chính sách/cam kết ràng buộc hiệu lực nào trong hệ thống"));

        Warehouse warehouse = Warehouse.builder()
                .owner(owner)
                .type(type)
                .name(request.getName())
                .address(request.getAddress())
                .provinceCode(normalizeOptionalText(request.getProvinceCode()))
                .provinceName(normalizeOptionalText(request.getProvinceName()))
                .districtCode(normalizeOptionalText(request.getDistrictCode()))
                .districtName(normalizeOptionalText(request.getDistrictName()))
                .description(request.getDescription())
                .capacity(request.getCapacity())
                .rentalPricingType(pricingType)
                .rentalPrice(rentalPrice)
                .status(WarehouseStatus.PENDING_APPROVAL)
                .isVerified(false)
                .policy(policy)
                .build();

        validateLocationFields(
                warehouse.getProvinceCode(),
                warehouse.getProvinceName(),
                warehouse.getDistrictCode(),
                warehouse.getDistrictName()
        );

        warehouse = warehouseRepository.save(warehouse);


        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            attachImages(warehouse, request.getImageUrls());
        }

        try {
            final String whName = warehouse.getName();
            final String ownerName = owner.getFullName() != null ? owner.getFullName() : owner.getEmail();
            userRepository.findFirstByRoles_Name(RoleType.ROLE_ADMIN.name())
                    .ifPresent(admin -> notificationService.push(
                            admin.getId(),
                            "Yêu cầu duyệt bài đăng kho mới",
                            "Chủ kho '" + ownerName + "' vừa đăng kho bãi mới '" + whName + "'. Vui lòng kiểm tra và phê duyệt.",
                            "WAREHOUSE"
                    ));
        } catch (Exception e) {
            log.warn("Failed to push new warehouse notification to admin: {}", e.getMessage());
        }

        log.info("Warehouse created: {} (ID: {})", warehouse.getName(), warehouse.getId());
        return mapToResponse(warehouse);
    }




    @Transactional
    public WarehouseResponse updateWarehouse(UUID ownerId, UUID warehouseId, UpdateWarehouseRequest request) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        boolean addressChanged = StringUtils.hasText(request.getAddress());
        boolean structuredLocationProvided = hasStructuredLocation(request);

        if (StringUtils.hasText(request.getName())) {
            warehouse.setName(request.getName().trim());
        }
        if (addressChanged) {
            warehouse.setAddress(request.getAddress().trim());
        }
        if (addressChanged && !structuredLocationProvided) {
            clearNormalizedLocation(warehouse);
        } else if (structuredLocationProvided) {
            applyUpdatedLocation(warehouse, request);
        }
        if (request.getDescription() != null) {
            warehouse.setDescription(request.getDescription().trim());
        }
        if (request.getCapacity() != null) {
            warehouse.setCapacity(request.getCapacity());
        }
        boolean pricingChanged = request.getRentalPricingType() != null
                || request.getRentalPrice() != null;
        if (pricingChanged) {
            RentalPricingType pricingType = request.getRentalPricingType() != null
                    ? request.getRentalPricingType()
                    : effectivePricingType(warehouse);
            java.math.BigDecimal rentalPrice = request.getRentalPrice();
            if (request.getRentalPrice() == null
                    && pricingType == RentalPricingType.NEGOTIATED) {
                rentalPrice = null;
            } else if (rentalPrice == null) {
                rentalPrice = warehouse.getRentalPrice();
            }
            validateRentalPricing(pricingType, rentalPrice);
            warehouse.setRentalPricingType(pricingType);
            warehouse.setRentalPrice(rentalPrice);
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





    @Transactional
    public void deleteWarehouse(UUID ownerId, UUID warehouseId) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        if (warehouseRepository.hasCurrentActiveContract(warehouseId)) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_HAS_ACTIVE_CONTRACTS);
        }

        warehouse.setDeleted(true);
        warehouseRepository.save(warehouse);
        log.info("Owner {} deleted warehouse {}", ownerId, warehouseId);
    }





    @Transactional
    public WarehouseResponse updateStatus(UUID ownerId, UUID warehouseId, WarehouseStatus newStatus) {
        if (newStatus == WarehouseStatus.PENDING_APPROVAL) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }

        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);
        warehouse.setStatus(newStatus);
        warehouse = warehouseRepository.save(warehouse);

        log.info("Owner {} updated warehouse {} status to {}", ownerId, warehouseId, newStatus);
        return mapToResponse(warehouse);
    }




    @Transactional(readOnly = true)
    public PagedResponse<WarehouseResponse> getMyWarehouses(UUID ownerId, int page, int size, String sortBy, String sortDir) {
        String normalizedSortBy = normalizeSortProperty(sortBy);
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(normalizedSortBy).ascending()
                : Sort.by(normalizedSortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Warehouse> warehousePage = warehouseRepository.findByOwnerId(ownerId, pageable);
        return toPagedResponse(warehousePage);
    }







    @Transactional
    public List<String> addImages(UUID ownerId, UUID warehouseId, List<String> imageUrls) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        int currentCount = warehouseImageRepository.countByWarehouseId(warehouseId);
        if (currentCount + imageUrls.size() > MAX_IMAGES_PER_WAREHOUSE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_IMAGE_LIMIT_EXCEEDED);
        }

        List<String> saved = attachImages(warehouse, imageUrls);
        log.info("Added {} images to warehouse {}", imageUrls.size(), warehouseId);
        return saved;
    }




    @Transactional
    public List<String> replaceImages(UUID ownerId, UUID warehouseId, List<String> imageUrls) {
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);

        warehouseImageRepository.deleteAllByWarehouseId(warehouseId);
        warehouse.getImages().clear();

        List<String> saved = attachImages(warehouse, imageUrls);
        log.info("Replaced images for warehouse {}", warehouseId);
        return saved;
    }






    @Transactional(readOnly = true)
    public PagedResponse<WarehouseResponse> searchWarehouses(WarehouseSearchRequest request,
                                                    int page, int size, String sortBy, String sortDir) {
        String normalizedSortBy = normalizeSortProperty(sortBy);
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(normalizedSortBy).ascending()
                : Sort.by(normalizedSortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        String kw = StringUtils.hasText(request.getKeyword()) ? "%" + request.getKeyword().trim().toLowerCase() + "%" : null;

        Page<Warehouse> result = warehouseRepository.searchPublic(
                kw,
                WarehouseStatus.AVAILABLE,
                request.getEffectiveMinRentalPrice(),
                request.getEffectiveMaxRentalPrice(),
                request.getMinCapacity(),
                request.getMaxCapacity(),
                request.getProvinceCode(),
                request.getDistrictCode(),
                request.getWarehouseTypeId(),
                request.getIsVerified(),
                pageable
        );

        return toPagedResponse(result);
    }





    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseDetail(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findPublicAvailableById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public WarehouseOwnerContactResponse getOwnerContact(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findPublicAvailableById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        User owner = warehouse.getOwner();
        if (owner == null) {
            throw new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        return WarehouseOwnerContactResponse.builder()
                .warehouseId(warehouse.getId())
                .ownerId(owner.getId())
                .ownerName(owner.getFullName())
                .phone(owner.getPhone())
                .build();
    }







    @Transactional
    public WarehouseResponse verifyWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (warehouse.getStatus() != WarehouseStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Kho bãi đã được duyệt bài đăng hoặc đang hoạt động");
        }

        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setRejectReason(null);
        warehouse = warehouseRepository.save(warehouse);

        if (warehouse.getOwner() != null) {
            notificationService.push(
                    warehouse.getOwner().getId(),
                    "Bài đăng kho bãi đã được duyệt",
                    "Chúc mừng! Yêu cầu đăng kho bãi '" + warehouse.getName() + "' của bạn đã được duyệt thành công và hiện đang hiển thị trên hệ thống.",
                    "SYSTEM"
            );
        }

        log.info("Admin approved listing for warehouse {}", warehouseId);
        return mapToResponse(warehouse);
    }




    @Transactional
    public WarehouseResponse rejectWarehouse(UUID warehouseId, String reason) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setStatus(WarehouseStatus.INACTIVE);
        if (StringUtils.hasText(reason)) {
            warehouse.setRejectReason(reason.trim());
        }
        warehouse = warehouseRepository.save(warehouse);

        if (warehouse.getOwner() != null) {
            String message = StringUtils.hasText(reason)
                    ? "Yêu cầu đăng kho bãi '" + warehouse.getName() + "' của bạn không được phê duyệt. Lý do từ chối: " + reason.trim()
                    : "Yêu cầu đăng kho bãi '" + warehouse.getName() + "' của bạn không được phê duyệt. Vui lòng kiểm tra lại thông tin.";

            notificationService.push(
                    warehouse.getOwner().getId(),
                    "Bài đăng kho bãi không được duyệt",
                    message,
                    "SYSTEM"
            );
        }

        log.info("Admin rejected warehouse listing {} with reason: {}", warehouseId, reason);
        return mapToResponse(warehouse);
    }




    @Transactional(readOnly = true)
    public PagedResponse<WarehouseResponse> getAllWarehouses(WarehouseSearchRequest request,
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





    @Transactional
    public void markAsVerifiedByInspection(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setVerified(true);
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouseRepository.save(warehouse);
        log.info("Warehouse {} verified via inspection", warehouseId);
    }





    private Warehouse getOwnedWarehouse(UUID ownerId, UUID warehouseId) {
        return warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED));
    }

    @Transactional(readOnly = true)
    public Warehouse getOwnedWarehouseForContract(UUID ownerId, UUID warehouseId) {
        return getOwnedWarehouse(ownerId, warehouseId);
    }

    /**
     * Locks a warehouse row for direct-contract submission. Contract overlap
     * checks are serialized per warehouse without locking unrelated warehouses.
     */
    @Transactional
    public Warehouse lockWarehouseForContractSubmit(UUID warehouseId) {
        return warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    private RentalPricingType resolvePricingType(RentalPricingType requestedType) {
        return requestedType != null ? requestedType : RentalPricingType.FIXED_MONTHLY;
    }

    private RentalPricingType effectivePricingType(Warehouse warehouse) {
        return warehouse.getRentalPricingType() != null
                ? warehouse.getRentalPricingType()
                : RentalPricingType.FIXED_MONTHLY;
    }

    private void validateRentalPricing(RentalPricingType pricingType,
                                       java.math.BigDecimal rentalPrice) {
        if (pricingType == RentalPricingType.NEGOTIATED) {
            if (rentalPrice != null) {
                throw new BadRequestException(
                        "rentalPrice must be omitted when rentalPricingType is NEGOTIATED");
            }
            return;
        }
        if (rentalPrice == null || rentalPrice.signum() <= 0) {
            throw new BadRequestException(
                    "rentalPrice is required and must be greater than 0 for the selected pricing type");
        }
    }

    private String normalizeSortProperty(String sortBy) {
        return sortBy;
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

        java.math.BigDecimal rentalPrice = w.getRentalPrice();
        RentalPricingType pricingType = effectivePricingType(w);
        String publicationStatus = resolvePublicationStatus(w);
        boolean publishableWarehouse = w.isActive()
                && !w.isDeleted()
                && w.isVerified()
                && w.getStatus() == WarehouseStatus.AVAILABLE;

        return WarehouseResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .address(w.getAddress())
                .provinceCode(w.getProvinceCode())
                .provinceName(w.getProvinceName())
                .districtCode(w.getDistrictCode())
                .districtName(w.getDistrictName())
                .description(w.getDescription())
                .capacity(w.getCapacity())
                .rentalPrice(rentalPrice)
                .rentalPricingType(pricingType)
                .status(w.getStatus().name())
                .rejectReason(w.getRejectReason())
                .isVerified(w.isVerified())
                .typeId(w.getType() != null ? w.getType().getId() : null)
                .typeName(w.getType() != null ? w.getType().getName() : null)
                .ownerId(w.getOwner() != null ? w.getOwner().getId() : null)
                .ownerName(w.getOwner() != null ? w.getOwner().getFullName() : null)
                .coverImageUrl(cover)
                .imageUrls(urls)
                .policyId(w.getPolicy() != null ? w.getPolicy().getId() : null)
                .policyVersion(w.getPolicy() != null ? w.getPolicy().getVersion() : null)
                .publishedAt(w.getPublishedAt())
                .visibleUntil(w.getVisibleUntil())
                .publicationStatus(publicationStatus)
                .canPublish(publishableWarehouse && PUBLICATION_DRAFT.equals(publicationStatus))
                .canRenew(publishableWarehouse
                        && (PUBLICATION_PUBLISHED.equals(publicationStatus)
                        || PUBLICATION_EXPIRED.equals(publicationStatus)))
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private boolean hasStructuredLocation(UpdateWarehouseRequest request) {
        return StringUtils.hasText(request.getProvinceCode())
                || StringUtils.hasText(request.getProvinceName())
                || StringUtils.hasText(request.getDistrictCode())
                || StringUtils.hasText(request.getDistrictName());
    }

    private void applyUpdatedLocation(Warehouse warehouse, UpdateWarehouseRequest request) {
        String provinceCode = StringUtils.hasText(request.getProvinceCode())
                ? normalizeOptionalText(request.getProvinceCode()) : warehouse.getProvinceCode();
        String provinceName = StringUtils.hasText(request.getProvinceName())
                ? normalizeOptionalText(request.getProvinceName()) : warehouse.getProvinceName();
        String districtCode = StringUtils.hasText(request.getDistrictCode())
                ? normalizeOptionalText(request.getDistrictCode()) : warehouse.getDistrictCode();
        String districtName = StringUtils.hasText(request.getDistrictName())
                ? normalizeOptionalText(request.getDistrictName()) : warehouse.getDistrictName();

        validateLocationFields(provinceCode, provinceName, districtCode, districtName);
        warehouse.setProvinceCode(provinceCode);
        warehouse.setProvinceName(provinceName);
        warehouse.setDistrictCode(districtCode);
        warehouse.setDistrictName(districtName);
    }

    private void validateLocationFields(String provinceCode, String provinceName,
                                        String districtCode, String districtName) {
        boolean hasProvinceCode = StringUtils.hasText(provinceCode);
        boolean hasProvinceName = StringUtils.hasText(provinceName);
        boolean hasDistrictCode = StringUtils.hasText(districtCode);
        boolean hasDistrictName = StringUtils.hasText(districtName);

        if (hasProvinceCode != hasProvinceName) {
            throw new BadRequestException("Province code and province name must be provided together");
        }
        if (hasDistrictCode != hasDistrictName) {
            throw new BadRequestException("District code and district name must be provided together");
        }
        if (hasDistrictCode && !hasProvinceCode) {
            throw new BadRequestException("Province must be provided when district is provided");
        }
    }

    private void clearNormalizedLocation(Warehouse warehouse) {
        warehouse.setProvinceCode(null);
        warehouse.setProvinceName(null);
        warehouse.setDistrictCode(null);
        warehouse.setDistrictName(null);
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolvePublicationStatus(Warehouse warehouse) {
        if (warehouse.getPublishedAt() == null || warehouse.getVisibleUntil() == null) {
            return PUBLICATION_DRAFT;
        }
        return warehouse.getVisibleUntil().isBefore(LocalDateTime.now())
                ? PUBLICATION_EXPIRED
                : PUBLICATION_PUBLISHED;
    }

    private PagedResponse<WarehouseResponse> toPagedResponse(Page<Warehouse> page) {
        List<WarehouseResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedResponse.<WarehouseResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
