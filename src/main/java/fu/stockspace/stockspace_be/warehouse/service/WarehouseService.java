package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
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
    private static final String PUBLICATION_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String PUBLICATION_PUBLISHED = "PUBLISHED";
    private static final String PUBLICATION_EXPIRED = "EXPIRED";
    private static final String PUBLICATION_REFUNDED = "REFUNDED";

    private final WarehouseRepository warehouseRepository;
    private final WarehouseTypeRepository warehouseTypeRepository;
    private final WarehouseImageRepository warehouseImageRepository;
    private final UserRepository userRepository;
    private final SystemPolicyRepository systemPolicyRepository;
    private final NotificationService notificationService;
    private final TenantWarehouseAccessService tenantWarehouseAccessService;
    private final ListingOrderRepository listingOrderRepository;
    private final WarehouseLayoutRepository warehouseLayoutRepository;

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
                .status(WarehouseStatus.DRAFT)
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
        Warehouse warehouse = getOwnedWarehouse(ownerId, warehouseId);
        if (newStatus == null
                || newStatus == WarehouseStatus.PENDING_APPROVAL
                || (warehouse.getStatus() == WarehouseStatus.PENDING_APPROVAL
                && newStatus == WarehouseStatus.AVAILABLE)) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }
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
        return toPagedResponse(warehousePage, true);
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
    public WarehouseResponse approveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (warehouse.getStatus() != WarehouseStatus.PENDING_APPROVAL) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }

        validateDefaultLayoutForApproval(warehouseId);

        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setRejectReason(null);
        warehouse.setPublishedAt(null);
        warehouse.setVisibleUntil(null);
        warehouse = warehouseRepository.save(warehouse);

        if (warehouse.getOwner() != null) {
            notificationService.push(
                    warehouse.getOwner().getId(),
                    "Bài đăng kho bãi đã được duyệt",
                    "Chúc mừng! Bài đăng kho bãi '" + warehouse.getName()
                            + "' đã được duyệt. Bạn có thể chọn ngày và gói đăng bài.",
                    "SYSTEM"
            );
        }

        log.info("Admin approved warehouse {} before publication payment", warehouseId);
        return mapToResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse submitForApproval(UUID ownerId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseOwner(warehouse, ownerId);

        if (warehouse.getStatus() != WarehouseStatus.DRAFT
                && warehouse.getStatus() != WarehouseStatus.INACTIVE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }

        validateDefaultLayoutForApproval(warehouseId);

        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        warehouse.setRejectReason(null);
        warehouse.setPublishedAt(null);
        warehouse.setVisibleUntil(null);
        warehouse = warehouseRepository.save(warehouse);

        notifyAdminForApproval(warehouse);

        log.info("Owner {} submitted warehouse {} for approval", ownerId, warehouseId);
        return mapToResponse(warehouse);
    }

    private void validateDefaultLayoutForApproval(UUID warehouseId) {
        warehouseLayoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId)
                .filter(layout -> layout.isActive()
                        && !layout.isDeleted()
                        && layout.getWidth() != null
                        && layout.getWidth().signum() > 0
                        && layout.getLength() != null
                        && layout.getLength().signum() > 0
                        && layout.getHeight() != null
                        && layout.getHeight().signum() > 0)
                .orElseThrow(() -> new ResourceConflictException(
                        ErrorCode.WAREHOUSE_DEFAULT_LAYOUT_REQUIRED));
    }

    private void notifyAdminForApproval(Warehouse warehouse) {
        try {
            userRepository.findFirstByRoles_Name("ROLE_ADMIN")
                    .ifPresent(admin -> notificationService.push(
                            admin.getId(),
                            "Warehouse listing awaiting review",
                            "Owner submitted warehouse '" + warehouse.getName()
                                    + "' for content approval.",
                            "WAREHOUSE"));
        } catch (Exception exception) {
            log.warn("Failed to push warehouse approval notification: {}", exception.getMessage());
        }
    }

    private void requireWarehouseOwner(Warehouse warehouse, UUID ownerId) {
        if (warehouse.getOwner() == null || !ownerId.equals(warehouse.getOwner().getId())) {
            throw new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
    }

    @Transactional
    public WarehouseResponse rejectWarehouse(UUID warehouseId, String reason) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (warehouse.getStatus() != WarehouseStatus.PENDING_APPROVAL) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_INVALID_STATUS_TRANSITION);
        }

        warehouse.setStatus(WarehouseStatus.INACTIVE);
        if (StringUtils.hasText(reason)) {
            warehouse.setRejectReason(reason.trim());
        }
        warehouse.setPublishedAt(null);
        warehouse.setVisibleUntil(null);
        warehouse = warehouseRepository.save(warehouse);

        if (warehouse.getOwner() != null) {
            String message = StringUtils.hasText(reason)
                    ? "Yêu cầu đăng kho bãi '" + warehouse.getName()
                            + "' của bạn không được phê duyệt. Lý do từ chối: " + reason.trim()
                            + ". Bạn có thể chỉnh sửa và gửi lại sau."
                    : "Yêu cầu đăng kho bãi '" + warehouse.getName()
                            + "' của bạn không được phê duyệt. Vui lòng kiểm tra lại thông tin. Phí đăng bài "
                            + "Bạn có thể chỉnh sửa và gửi lại sau.";

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

        return toPagedResponse(result, true);
    }





    @Transactional
    public void markAsVerifiedByInspection(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        warehouse.setVerified(true);
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
        return mapToResponse(w, null, null);
    }

    private WarehouseResponse mapToResponse(
            Warehouse w,
            UUID currentListingOrderId,
            ListingOrderStatus currentListingOrderStatus
    ) {
        List<String> urls = w.getImages().stream()
                .map(WarehouseImage::getImageUrl)
                .collect(Collectors.toList());

        String cover = urls.isEmpty() ? null : urls.get(0);

        java.math.BigDecimal rentalPrice = w.getRentalPrice();
        RentalPricingType pricingType = effectivePricingType(w);
        String publicationStatus = resolvePublicationStatus(w, currentListingOrderStatus);
        boolean canStartPublication = w.isActive()
                && !w.isDeleted()
                && w.getStatus() != WarehouseStatus.INACTIVE;
        boolean canRenewPublication = w.isActive()
                && !w.isDeleted()
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
                .canPublish(canStartPublication
                        && PUBLICATION_DRAFT.equals(publicationStatus)
                        && currentListingOrderStatus != ListingOrderStatus.PENDING_APPROVAL)
                .canRenew(canRenewPublication
                        && (PUBLICATION_PUBLISHED.equals(publicationStatus)
                        || PUBLICATION_EXPIRED.equals(publicationStatus)))
                .currentListingOrderId(currentListingOrderId)
                .currentListingOrderStatus(currentListingOrderStatus)
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

    private String resolvePublicationStatus(
            Warehouse warehouse,
            ListingOrderStatus currentListingOrderStatus
    ) {
        if (currentListingOrderStatus == ListingOrderStatus.PENDING_APPROVAL) {
            return PUBLICATION_PENDING_APPROVAL;
        }
        if (currentListingOrderStatus == ListingOrderStatus.REFUNDED
                && warehouse.getStatus() == WarehouseStatus.INACTIVE) {
            return PUBLICATION_REFUNDED;
        }
        if (warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            return PUBLICATION_DRAFT;
        }
        if (warehouse.getPublishedAt() == null || warehouse.getVisibleUntil() == null) {
            return PUBLICATION_DRAFT;
        }
        return warehouse.getVisibleUntil().isBefore(LocalDateTime.now())
                ? PUBLICATION_EXPIRED
                : PUBLICATION_PUBLISHED;
    }

    private PagedResponse<WarehouseResponse> toPagedResponse(Page<Warehouse> page) {
        return toPagedResponse(page, false);
    }

    private PagedResponse<WarehouseResponse> toPagedResponse(Page<Warehouse> page, boolean includeListingOrderState) {
        Map<UUID, ListingOrderRepository.LatestListingOrderState> latestStates = includeListingOrderState
                ? findLatestListingOrderStates(page.getContent())
                : Collections.emptyMap();
        List<WarehouseResponse> content = page.getContent().stream()
                .map(warehouse -> {
                    ListingOrderRepository.LatestListingOrderState latest = latestStates.get(warehouse.getId());
                    return latest == null
                            ? mapToResponse(warehouse)
                            : mapToResponse(warehouse, latest.getOrderId(), latest.getStatus());
                })
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

    private Map<UUID, ListingOrderRepository.LatestListingOrderState> findLatestListingOrderStates(
            List<Warehouse> warehouses
    ) {
        List<UUID> warehouseIds = warehouses.stream()
                .map(Warehouse::getId)
                .filter(Objects::nonNull)
                .toList();
        if (warehouseIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return listingOrderRepository.findLatestStateByWarehouseIds(warehouseIds).stream()
                .collect(Collectors.toMap(
                        ListingOrderRepository.LatestListingOrderState::getWarehouseId,
                        state -> state,
                        (first, ignored) -> first
                ));
    }
}
