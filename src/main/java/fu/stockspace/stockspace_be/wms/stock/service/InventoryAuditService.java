package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.*;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAuditService {

    private final InventoryAuditRepository auditRepository;
    private final InventoryAuditItemRepository auditItemRepository;
    private final StockBatchRepository stockBatchRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ProductSkuRepository productSkuRepository;
    private final InventoryReceiptService inventoryReceiptService;
    private final NotificationService notificationService;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;

    @Transactional
    public InventoryAuditResponse createAudit(UUID userId, CreateInventoryAuditRequest request) {
        User requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        UUID tenantId = resolveTenantId(userId);
        requireWarehouseMutationAccess(requestedBy, tenantId, warehouse.getId());

        InventoryAudit audit = InventoryAudit.builder()
                .warehouse(warehouse)
                .requestedBy(requestedBy)
                .status(AuditStatus.PENDING)
                .note(request.getNote())
                .build();
        audit = auditRepository.save(audit);

        List<StockBatch> batches = stockBatchRepository.findByWarehouseIdAndTenantId(
                warehouse.getId(), tenantId, Pageable.unpaged()).getContent();

        final InventoryAudit savedAudit = audit;
        List<InventoryAuditItem> items = batches.stream()
                .map(batch -> InventoryAuditItem.builder()
                        .audit(savedAudit)
                        .batch(batch)
                        .expectedQuantity(batch.getQuantity())
                        .actualQuantity(null)
                        .discrepancy(null)
                        .build())
                .collect(Collectors.toList());
        auditItemRepository.saveAll(items);

        try {
            final String whName = warehouse.getName();
            if (userId.equals(tenantId)) {
                // Tenant tạo phiếu -> thông báo cho các Staff
                List<TenantMember> staffList = tenantMemberRepository.findActiveStaffsOrderByJoinedAtAsc(tenantId);
                for (TenantMember staff : staffList) {
                    if (staff.getUser() != null) {
                        notificationService.push(
                                staff.getUser().getId(),
                                "Lệnh kiểm kê kho mới",
                                "Có phiếu kiểm kê mới cho kho '" + whName + "'. Vui lòng tiến hành kiểm đếm hàng hóa.",
                                "AUDIT"
                        );
                    }
                }
            } else {
                // Staff tạo phiếu -> thông báo cho Tenant
                String creatorName = requestedBy.getFullName() != null ? requestedBy.getFullName() : requestedBy.getEmail();
                notificationService.push(
                        tenantId,
                        "Phiếu kiểm kê kho mới",
                        "Nhân viên '" + creatorName + "' vừa tạo phiếu kiểm kê cho kho '" + whName + "'.",
                        "AUDIT"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to push create audit notification: {}", e.getMessage());
        }

        log.info("InventoryAudit: Created audit {} for warehouse {} ({} batch lines snapshotted)",
                audit.getId(), warehouse.getId(), items.size());
        return mapToResponse(audit, items);
    }

    @Transactional
    public InventoryAuditResponse submitAudit(UUID userId, UUID auditId, SubmitAuditRequest request) {
        InventoryAudit audit = getAuditForUserForUpdate(auditId, userId);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        requireWarehouseMutationAccess(actor, resolveTenantId(userId), audit.getWarehouse().getId());

        if (audit.getStatus() != AuditStatus.PENDING) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }

        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);

        for (SubmitAuditItemRequest itemReq : request.getItems()) {
            items.stream()
                    .filter(i -> i.getBatch().getId().equals(itemReq.getBatchId()))
                    .findFirst()
                    .ifPresent(auditItem -> {
                        auditItem.setActualQuantity(itemReq.getActualQuantity());
                        auditItem.setDiscrepancy(itemReq.getActualQuantity() - auditItem.getExpectedQuantity());
                        auditItem.setNote(itemReq.getNote());
                        auditItemRepository.save(auditItem);
                    });
        }

        audit.setStatus(AuditStatus.SUBMITTED);
        audit = auditRepository.save(audit);

        try {
            final String whName = audit.getWarehouse().getName();
            UUID tenantId = tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId)
                    .map(member -> member.getTenant().getId())
                    .orElse(userId);

            notificationService.push(
                    tenantId,
                    "Kết quả kiểm kê đã được nộp",
                    "Kết quả kiểm đếm cho kho '" + whName + "' đã được nộp. Vui lòng kiểm tra đối soát và phê duyệt.",
                    "AUDIT"
            );
        } catch (Exception e) {
            log.warn("Failed to push submit audit notification: {}", e.getMessage());
        }

        List<InventoryAuditItem> updatedItems = auditItemRepository.findByAuditId(auditId);
        log.info("InventoryAudit: Audit {} submitted by user {}", auditId, userId);
        return mapToResponse(audit, updatedItems);
    }

    @Transactional
    public InventoryAuditResponse approveAudit(UUID approverId, UUID auditId) {
        InventoryAudit audit = auditRepository.findByIdForUpdate(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));

        if (audit.getStatus() != AuditStatus.SUBMITTED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ensureApproverIsTenant(approver);
        UUID tenantId = resolveTenantId(approverId);
        requireAuditTenantAccess(audit, tenantId);
        requireWarehouseMutationAccess(approver, tenantId, audit.getWarehouse().getId());

        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);
        UUID warehouseId = audit.getWarehouse().getId();

        for (InventoryAuditItem item : items) {
            if (item.getDiscrepancy() == null || item.getDiscrepancy() == 0)
                continue;

            int absDiscrepancy = Math.abs(item.getDiscrepancy());
            DocumentType type = item.getDiscrepancy() > 0 ? DocumentType.INBOUND : DocumentType.OUTBOUND;

            inventoryReceiptService.createAdjustmentReceipt(
                    approverId, auditId, warehouseId,
                    type, item.getBatch().getId(), absDiscrepancy);
        }

        audit.setApprovedBy(approver);
        audit.setStatus(AuditStatus.APPROVED);
        audit = auditRepository.save(audit);

        String warehouseName = audit.getWarehouse().getName();
        notificationService.push(
                audit.getRequestedBy().getId(),
                "Phiếu kiểm kê đã được duyệt",
                "Phiếu kiểm kê kho " + warehouseName + " đã được duyệt. Tồn kho đã được điều chỉnh tự động.",
                "AUDIT");

        log.info("InventoryAudit: Audit {} approved by user {}", auditId, approverId);
        return mapToResponse(audit, items);
    }

    @Transactional
    public InventoryAuditResponse rejectAudit(UUID approverId, UUID auditId, String reason) {
        InventoryAudit audit = auditRepository.findByIdForUpdate(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));

        if (audit.getStatus() != AuditStatus.SUBMITTED && audit.getStatus() != AuditStatus.PENDING) {
            throw new BadRequestException(ErrorCode.AUDIT_ALREADY_PROCESSED);
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ensureApproverIsTenant(approver);
        UUID tenantId = resolveTenantId(approverId);
        requireAuditTenantAccess(audit, tenantId);
        requireWarehouseMutationAccess(approver, tenantId, audit.getWarehouse().getId());

        audit.setApprovedBy(approver);
        audit.setStatus(AuditStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            audit.setNote(audit.getNote() != null
                    ? audit.getNote() + " | Lý do từ chối: " + reason
                    : "Lý do từ chối: " + reason);
        }
        audit = auditRepository.save(audit);

        String warehouseName = audit.getWarehouse().getName();
        notificationService.push(
                audit.getRequestedBy().getId(),
                "Phiếu kiểm kê bị từ chối",
                "Phiếu kiểm kê kho " + warehouseName + " bị từ chối. Lý do: "
                        + (reason != null ? reason : "Không có lý do cụ thể"),
                "AUDIT");

        log.info("InventoryAudit: Audit {} rejected by user {} (reason: {})", auditId, approverId, reason);
        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);
        return mapToResponse(audit, items);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryAuditResponse> getMyAudits(UUID userId, Pageable pageable) {
        return getMyAudits(userId, null, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryAuditResponse> getMyAudits(UUID userId, UUID warehouseId, Pageable pageable) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(userId);
        List<UUID> accessibleWarehouseIds = (isStaff(actor)
                ? accessService.findAccessibleContractWarehouses(tenantId, userId)
                : accessService.findActiveContractWarehouses(tenantId))
                .stream()
                .map(Warehouse::getId)
                .toList();

        Page<InventoryAudit> page;
        if (warehouseId != null) {
            requireWarehouseObservationAccess(actor, tenantId, warehouseId);
            page = auditRepository.findAuditsForTenant(
                    warehouseId, List.of(warehouseId), tenantId, pageable);
        } else if (!accessibleWarehouseIds.isEmpty()) {
            page = auditRepository.findAuditsForTenant(null, accessibleWarehouseIds, tenantId, pageable);
        } else {
            page = Page.empty(pageable);
        }

        return PagedResponse.fromPage(page, audit -> {
            List<InventoryAuditItem> items = auditItemRepository.findByAuditId(audit.getId());
            return mapToResponse(audit, items);
        });
    }

    @Transactional(readOnly = true)
    public InventoryAuditResponse getAuditDetail(UUID userId, UUID auditId) {
        InventoryAudit audit = getAuditForUser(auditId, userId);
        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);
        return mapToResponse(audit, items);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryAuditResponse> getAllAudits(Pageable pageable) {
        Page<InventoryAudit> page = auditRepository.findByIsDeletedFalse(pageable);
        return PagedResponse.fromPage(page, audit -> {
            List<InventoryAuditItem> items = auditItemRepository.findByAuditId(audit.getId());
            return mapToResponse(audit, items);
        });
    }

    private InventoryAudit getAuditForUser(UUID auditId, UUID userId) {
        InventoryAudit audit = auditRepository.findById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(userId);
        requireAuditTenantAccess(audit, tenantId);
        requireWarehouseObservationAccess(actor, tenantId, audit.getWarehouse().getId());
        return audit;
    }

    private InventoryAudit getAuditForUserForUpdate(UUID auditId, UUID userId) {
        InventoryAudit audit = auditRepository.findByIdForUpdate(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(userId);
        requireAuditTenantAccess(audit, tenantId);
        requireWarehouseObservationAccess(actor, tenantId, audit.getWarehouse().getId());
        return audit;
    }

    private void requireAuditTenantAccess(InventoryAudit audit, UUID tenantId) {
        UUID requesterId = audit.getRequestedBy().getId();
        boolean requestedByTenant = requesterId.equals(tenantId);
        boolean requestedByStaffOfTenant = tenantMemberRepository
                .findByUserIdOrderByJoinedAtDesc(requesterId)
                .stream()
                .anyMatch(member -> member.getTenant() != null
                        && tenantId.equals(member.getTenant().getId()));
        if (!requestedByTenant && !requestedByStaffOfTenant) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private UUID resolveTenantId(UUID userId) {
        return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId)
                .map(member -> member.getTenant().getId())
                .orElse(userId);
    }

    private void requireWarehouseObservationAccess(User actor, UUID tenantId, UUID warehouseId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (isStaff(actor)) {
            accessService.requireActiveStaffAssignment(actor.getId(), tenantId, warehouseId);
        }
    }

    private void requireWarehouseMutationAccess(User actor, UUID tenantId, UUID warehouseId) {
        requireWarehouseObservationAccess(actor, tenantId, warehouseId);
        accessService.requireActiveSubscription(tenantId);
    }

    private boolean isStaff(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName()));
    }

    private void ensureApproverIsTenant(User approver) {
        boolean isTenant = approver.getRoles() != null && approver.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_TENANT.name().equals(role.getName()));
        if (!isTenant) {
            throw new ForbiddenException(
                    "Chỉ Doanh nghiệp (Tenant) có quyền duyệt hoặc từ chối phiếu kiểm kê.");
        }
    }

    private InventoryAuditItemResponse mapItemToResponse(InventoryAuditItem item) {
        StockBatch batch = item.getBatch();
        ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(batch.getSkuId()).orElse(null);
        UnitOfMeasure uom = sku != null ? sku.getUom() : null;

        return InventoryAuditItemResponse.builder()
                .id(item.getId())
                .batchId(batch.getId())
                .skuCode(sku != null ? sku.getSkuCode() : null)
                .skuName(sku != null ? sku.getName() : null)
                .uomSymbol(uom != null ? uom.getCode() : null)
                .rackName(batch.getRack() != null ? batch.getRack().getName() : null)
                .binName(batch.getBin() != null ? batch.getBin().getName() : null)
                .expectedQuantity(item.getExpectedQuantity())
                .actualQuantity(item.getActualQuantity())
                .discrepancy(item.getDiscrepancy())
                .note(item.getNote())
                .build();
    }

    public InventoryAuditResponse mapToResponse(InventoryAudit audit, List<InventoryAuditItem> items) {
        List<InventoryAuditItemResponse> itemResponses = items.stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        return InventoryAuditResponse.builder()
                .id(audit.getId())
                .warehouseId(audit.getWarehouse().getId())
                .warehouseName(audit.getWarehouse().getName())
                .status(audit.getStatus())
                .note(audit.getNote())
                .requestedById(audit.getRequestedBy().getId())
                .requestedByName(audit.getRequestedBy().getFullName())
                .approvedById(audit.getApprovedBy() != null ? audit.getApprovedBy().getId() : null)
                .approvedByName(audit.getApprovedBy() != null ? audit.getApprovedBy().getFullName() : null)
                .createdAt(audit.getCreatedAt())
                .updatedAt(audit.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
