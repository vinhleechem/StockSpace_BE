package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.*;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditCountStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditAdjustment;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
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
    private final WarehouseRackRepository warehouseRackRepository;
    private final WarehouseBinRepository warehouseBinRepository;
    private final InventoryAuditLockService auditLockService;
    private final InventoryAuditAdjustmentRepository auditAdjustmentRepository;

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
        UUID skuId = batch != null ? batch.getSkuId() : item.getSkuId();
        ProductSku sku = skuId == null ? null : productSkuRepository.findByIdAndIsDeletedFalse(skuId).orElse(null);
        UnitOfMeasure uom = sku != null ? sku.getUom() : null;

        return InventoryAuditItemResponse.builder()
                .id(item.getId())
                .batchId(batch != null ? batch.getId() : null)
                .skuCode(sku != null ? sku.getSkuCode() : null)
                .skuName(sku != null ? sku.getName() : null)
                .uomSymbol(uom != null ? uom.getCode() : null)
                .rackName(batch != null && batch.getRack() != null ? batch.getRack().getName()
                        : item.getRack() != null ? item.getRack().getName() : null)
                .binName(batch != null && batch.getBin() != null ? batch.getBin().getName()
                        : item.getBin() != null ? item.getBin().getName() : null)
                .expectedQuantity(item.getExpectedQuantity())
                .actualQuantity(item.getActualQuantity())
                .discrepancy(item.getDiscrepancy())
                .note(item.getNote())
                .varianceReason(item.getVarianceReason())
                .arrivalDate(batch != null ? batch.getArrivalDate() : null)
                .countStatus(item.getCountStatus() == null ? AuditCountStatus.UNCOUNTED : item.getCountStatus())
                .countedById(item.getCountedBy() != null ? item.getCountedBy().getId() : null)
                .countedAt(item.getCountedAt())
                .countRound(item.getCountRound())
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
                .countRound(audit.getCountRound())
                .scopeType(audit.getScopeType())
                .assignedToId(audit.getAssignedTo() != null ? audit.getAssignedTo().getId() : null)
                .assignedToName(audit.getAssignedTo() != null ? audit.getAssignedTo().getFullName() : null)
                .startedAt(audit.getStartedAt())
                .submittedAt(audit.getSubmittedAt())
                .reviewedAt(audit.getReviewedAt())
                .reviewReason(audit.getReviewReason())
                .items(itemResponses)
                .build();
    }

    /* ----------------------------- v2 workflow ----------------------------- */

    @Transactional
    public InventoryAuditResponse createAuditV2(UUID userId, CreateInventoryAuditPlanRequest request) {
        User actor = findUser(userId);
        UUID tenantId = resolveTenantId(userId);
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseMutationAccess(actor, tenantId, warehouse.getId());

        WarehouseRack rack = null;
        WarehouseBin bin = null;
        validateScopeRequest(request);
        if (request.getRackId() != null) {
            rack = warehouseRackRepository.findByIdAndIsDeletedFalse(request.getRackId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            if (rack.getLayout() == null || rack.getLayout().getWarehouse() == null
                    || !warehouse.getId().equals(rack.getLayout().getWarehouse().getId())) {
                throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
            }
        }
        if (request.getBinId() != null) {
            bin = warehouseBinRepository.findByIdAndIsDeletedFalse(request.getBinId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            if (bin.getRack() == null || bin.getRack().getLayout() == null
                    || bin.getRack().getLayout().getWarehouse() == null
                    || !warehouse.getId().equals(bin.getRack().getLayout().getWarehouse().getId())
                    || (rack != null && !rack.getId().equals(bin.getRack().getId()))) {
                throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
            }
        }

        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TENANT_NOT_FOUND));
        User assignee = request.getAssignedToId() == null && isStaff(actor) ? actor
                : request.getAssignedToId() == null ? null
                : userRepository.findById(request.getAssignedToId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (isStaff(actor) && assignee != null && !actor.getId().equals(assignee.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (assignee != null && !assignee.getId().equals(tenantId)
                && !tenantMemberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(
                assignee.getId(), tenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        InventoryAudit audit = InventoryAudit.builder()
                .warehouse(warehouse)
                .tenant(tenant)
                .requestedBy(actor)
                .assignedTo(assignee)
                .scopeType(request.getScopeType() == null ? AuditScopeType.WAREHOUSE : request.getScopeType())
                .scopeRack(rack)
                .scopeBin(bin)
                .workflowVersion(2)
                .countRound(1)
                .status(AuditStatus.DRAFT)
                .note(request.getNote())
                .build();
        audit = auditRepository.save(audit);
        return mapToResponse(audit, List.of());
    }

    @Transactional
    public InventoryAuditResponse startAuditV2(UUID userId, UUID auditId) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2CountAccess(audit, actor);
        if (audit.getStatus() != AuditStatus.DRAFT && audit.getStatus() != AuditStatus.RECOUNT_REQUIRED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }

        if (audit.getStatus() == AuditStatus.RECOUNT_REQUIRED) {
            audit.setCountRound(audit.getCountRound() + 1);
        }
        auditLockService.acquire(audit);
        final InventoryAudit startedAudit = audit;
        List<StockBatch> batches = stockBatchRepository.findByWarehouseIdAndTenantId(
                        audit.getWarehouse().getId(), resolveAuditTenantId(audit), Pageable.unpaged())
                .getContent().stream()
                .filter(batch -> inScope(startedAudit, batch))
                .toList();
        // A counter verifies the physical quantity of one SKU at one location. Batch/lot
        // allocation is deliberately deferred to approval, otherwise one physical count
        // would be split into several rows and could not handle a FIFO shortage safely.
        Map<BatchScopeKey, List<StockBatch>> groupedBatches = batches.stream()
                .collect(Collectors.groupingBy(batch -> new BatchScopeKey(
                        batch.getSkuId(), idOf(batch.getRack()), idOf(batch.getBin()))));
        List<InventoryAuditItem> items = groupedBatches.entrySet().stream()
                .map(entry -> {
                    StockBatch representative = entry.getValue().get(0);
                    return InventoryAuditItem.builder()
                            .audit(startedAudit)
                            .skuId(entry.getKey().skuId())
                            .rack(representative.getRack())
                            .bin(representative.getBin())
                            .expectedQuantity(entry.getValue().stream()
                                    .mapToInt(StockBatch::getQuantity).sum())
                            .countRound(startedAudit.getCountRound())
                            .countStatus(AuditCountStatus.UNCOUNTED)
                            .build();
                })
                .collect(Collectors.toList());
        auditItemRepository.saveAll(items);
        if (audit.getStartedAt() == null) {
            audit.setStartedAt(LocalDateTime.now());
        }
        audit.setStatus(AuditStatus.IN_PROGRESS);
        audit.setReviewReason(null);
        audit = auditRepository.save(audit);
        return maskCounterResponse(mapToResponse(audit, items), actor);
    }

    @Transactional
    public InventoryAuditResponse saveAuditCountsV2(UUID userId, UUID auditId, SaveAuditCountsRequest request) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2CountAccess(audit, actor);
        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
        List<InventoryAuditItem> items = currentV2Items(audit);
        Set<UUID> knownIds = items.stream().map(InventoryAuditItem::getId).collect(Collectors.toSet());
        Set<UUID> submittedIds = new HashSet<>();
        for (SaveAuditCountItemRequest requestItem : request.getItems()) {
            if (!submittedIds.add(requestItem.getItemId()) || !knownIds.contains(requestItem.getItemId())) {
                throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
            }
            InventoryAuditItem item = items.stream()
                    .filter(candidate -> candidate.getId().equals(requestItem.getItemId()))
                    .findFirst().orElseThrow();
            item.setActualQuantity(requestItem.getActualQuantity());
            item.setDiscrepancy(requestItem.getActualQuantity() - item.getExpectedQuantity());
            item.setNote(requestItem.getNote());
            item.setVarianceReason(requestItem.getVarianceReason());
            item.setCountStatus(AuditCountStatus.COUNTED);
            item.setCountedBy(actor);
            item.setCountedAt(LocalDateTime.now());
            auditItemRepository.save(item);
        }
        return maskCounterResponse(mapToResponse(audit, currentV2Items(audit)), actor);
    }

    @Transactional
    public InventoryAuditResponse addUnexpectedItemV2(
            UUID userId, UUID auditId, AddUnexpectedAuditItemRequest request) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2CountAccess(audit, actor);
        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
        UUID tenantId = resolveAuditTenantId(audit);
        productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(request.getSkuId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        WarehouseRack rack = request.getRackId() == null ? audit.getScopeRack()
                : warehouseRackRepository.findByIdAndIsDeletedFalse(request.getRackId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
        WarehouseBin bin = request.getBinId() == null ? audit.getScopeBin()
                : warehouseBinRepository.findByIdAndIsDeletedFalse(request.getBinId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
        if (rack == null && bin != null) {
            rack = bin.getRack();
        }
        if (rack != null && (rack.getLayout() == null || rack.getLayout().getWarehouse() == null
                || !audit.getWarehouse().getId().equals(rack.getLayout().getWarehouse().getId()))) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (bin != null && (bin.getRack() == null || bin.getRack().getLayout() == null
                || bin.getRack().getLayout().getWarehouse() == null
                || !audit.getWarehouse().getId().equals(
                bin.getRack().getLayout().getWarehouse().getId()))) {
                throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (audit.getScopeType() == AuditScopeType.WAREHOUSE && rack == null && bin == null) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (audit.getScopeType() == AuditScopeType.RACK
                && (rack == null || !rack.getId().equals(audit.getScopeRack().getId()))) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (audit.getScopeType() == AuditScopeType.BIN
                && (bin == null || !bin.getId().equals(audit.getScopeBin().getId()))) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }

        final WarehouseRack resolvedRack = rack;
        final WarehouseBin resolvedBin = bin;
        List<InventoryAuditItem> current = currentV2Items(audit);
        boolean duplicate = current.stream().anyMatch(item -> request.getSkuId().equals(item.getSkuId())
                && ((item.getBin() == null && resolvedBin == null)
                || (item.getBin() != null && resolvedBin != null && item.getBin().getId().equals(resolvedBin.getId())))
                && ((item.getRack() == null && resolvedRack == null)
                || (item.getRack() != null && resolvedRack != null && item.getRack().getId().equals(resolvedRack.getId()))));
        if (duplicate) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        InventoryAuditItem item = InventoryAuditItem.builder()
                .audit(audit).skuId(request.getSkuId()).rack(resolvedRack).bin(resolvedBin)
                .expectedQuantity(0).actualQuantity(request.getActualQuantity())
                .discrepancy(request.getActualQuantity()).countRound(audit.getCountRound())
                .countStatus(AuditCountStatus.COUNTED).countedBy(actor)
                .countedAt(LocalDateTime.now()).note(request.getNote()).build();
        auditItemRepository.save(item);
        current.add(item);
        return maskCounterResponse(mapToResponse(audit, current), actor);
    }

    @Transactional
    public InventoryAuditResponse submitAuditV2(UUID userId, UUID auditId) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2CountAccess(audit, actor);
        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
        List<InventoryAuditItem> items = currentV2Items(audit);
        if (items.stream().anyMatch(item -> item.getActualQuantity() == null
                || item.getCountStatus() != AuditCountStatus.COUNTED)) {
            throw new BadRequestException(ErrorCode.AUDIT_COUNT_INCOMPLETE);
        }
        audit.setStatus(AuditStatus.SUBMITTED);
        audit.setSubmittedAt(LocalDateTime.now());
        audit = auditRepository.save(audit);
        pushAuditNotification(audit.getTenant() != null ? audit.getTenant().getId() : resolveAuditTenantId(audit),
                "Kết quả kiểm kê đã được nộp", "Phiếu kiểm kê kho " + audit.getWarehouse().getName()
                        + " đã sẵn sàng để đối soát.");
        return maskCounterResponse(mapToResponse(audit, items), actor);
    }

    @Transactional
    public InventoryAuditResponse requestRecountV2(UUID userId, UUID auditId, String reason) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2TenantReviewer(audit, actor);
        if (audit.getStatus() != AuditStatus.SUBMITTED || reason == null || reason.isBlank()) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
        audit.setStatus(AuditStatus.RECOUNT_REQUIRED);
        audit.setReviewReason(reason.trim());
        audit.setReviewedAt(LocalDateTime.now());
        audit = auditRepository.save(audit);
        auditLockService.release(auditId);
        pushAuditNotification(audit.getAssignedTo() != null ? audit.getAssignedTo().getId() : audit.getRequestedBy().getId(),
                "Yêu cầu kiểm kê lại", "Phiếu kiểm kê kho " + audit.getWarehouse().getName()
                        + " cần được đếm lại: " + reason.trim());
        return maskCounterResponse(mapToResponse(audit, currentV2Items(audit)), actor);
    }

    @Transactional
    public InventoryAuditResponse cancelAuditV2(UUID userId, UUID auditId, String reason) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User actor = findUser(userId);
        requireV2TenantReviewer(audit, actor);
        if (audit.getStatus() == AuditStatus.APPROVED || audit.getStatus() == AuditStatus.CANCELLED) {
            throw new BadRequestException(ErrorCode.AUDIT_ALREADY_PROCESSED);
        }
        audit.setStatus(AuditStatus.CANCELLED);
        audit.setCancelledAt(LocalDateTime.now());
        audit.setReviewedAt(LocalDateTime.now());
        audit.setReviewReason(reason == null ? null : reason.trim());
        audit = auditRepository.save(audit);
        auditLockService.release(auditId);
        return maskCounterResponse(mapToResponse(audit, currentV2Items(audit)), actor);
    }

    @Transactional
    public InventoryAuditResponse approveAuditV2(UUID userId, UUID auditId) {
        InventoryAudit audit = getV2ForUpdate(auditId);
        User approver = findUser(userId);
        requireV2TenantReviewer(audit, approver);
        if (audit.getStatus() != AuditStatus.SUBMITTED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }
        if (audit.getAssignedTo() != null && userId.equals(audit.getAssignedTo().getId())) {
            throw new ForbiddenException("Người thực hiện kiểm kê không được tự duyệt phiếu.");
        }
        if (!auditLockService.isLockedBy(auditId, audit.getWarehouse().getId())) {
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED,
                    "Phiếu kiểm kê không còn giữ khóa kho; cần mở một vòng kiểm kê mới.");
        }

        List<InventoryAuditItem> items = currentV2Items(audit);
        for (InventoryAuditItem item : items) {
            if (item.getActualQuantity() == null) {
                throw new BadRequestException(ErrorCode.AUDIT_COUNT_INCOMPLETE);
            }
            reconcileAuditItem(userId, auditId, audit, item);
        }
        audit.setApprovedBy(approver);
        audit.setReviewedAt(LocalDateTime.now());
        audit.setStatus(AuditStatus.APPROVED);
        audit = auditRepository.save(audit);
        auditLockService.release(auditId);
        pushAuditNotification(audit.getRequestedBy().getId(), "Phiếu kiểm kê đã được duyệt",
                "Tồn kho kho " + audit.getWarehouse().getName() + " đã được đối soát.");
        return mapToResponse(audit, items);
    }

    @Transactional(readOnly = true)
    public InventoryAuditResponse getAuditDetailV2(UUID userId, UUID auditId) {
        InventoryAudit audit = getV2ForRead(auditId);
        User actor = findUser(userId);
        requireV2ReadAccess(audit, actor);
        return maskCounterResponse(mapToResponse(audit, currentV2Items(audit)), actor);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryAuditResponse> getAuditsV2(UUID userId, UUID warehouseId, Pageable pageable) {
        User actor = findUser(userId);
        UUID tenantId = resolveTenantId(userId);
        List<UUID> accessibleWarehouseIds = (isStaff(actor)
                ? accessService.findAccessibleContractWarehouses(tenantId, userId)
                : accessService.findActiveContractWarehouses(tenantId))
                .stream().map(Warehouse::getId).toList();
        Page<InventoryAudit> page;
        if (warehouseId != null) {
            requireWarehouseObservationAccess(actor, tenantId, warehouseId);
            page = auditRepository.findV2AuditsForTenant(warehouseId, List.of(warehouseId), tenantId, pageable);
        } else if (accessibleWarehouseIds.isEmpty()) {
            page = Page.empty(pageable);
        } else {
            page = auditRepository.findV2AuditsForTenant(null, accessibleWarehouseIds, tenantId, pageable);
        }
        return PagedResponse.fromPage(page, audit ->
                maskCounterResponse(mapToResponse(audit, currentV2Items(audit)), actor));
    }

    private InventoryAudit getV2ForUpdate(UUID auditId) {
        return auditRepository.findV2ByIdForUpdate(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
    }

    private InventoryAudit getV2ForRead(UUID auditId) {
        return auditRepository.findById(auditId)
                .filter(audit -> audit.getWorkflowVersion() == 2 && !audit.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));
    }

    private List<InventoryAuditItem> currentV2Items(InventoryAudit audit) {
        return auditItemRepository.findByAuditIdAndCountRoundOrderById(audit.getId(), audit.getCountRound());
    }

    private void reconcileAuditItem(UUID userId, UUID auditId, InventoryAudit audit, InventoryAuditItem item) {
        UUID skuId = item.getSkuId() != null ? item.getSkuId()
                : item.getBatch() == null ? null : item.getBatch().getSkuId();
        if (skuId == null) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }

        List<StockBatch> candidates;
        if (item.getBatch() != null) {
            candidates = List.of(item.getBatch()); // compatibility with legacy/manual v2 rows
        } else {
            candidates = stockBatchRepository
                    .findAllBySkuIdAndWarehouseIdAndIsActiveTrueAndIsDeletedFalse(
                            skuId, audit.getWarehouse().getId()).stream()
                    .filter(batch -> sameLocation(batch, item) && inScope(audit, batch))
                    .sorted(fifoComparator())
                    .toList();
        }

        List<StockBatch> lockedBatches = new ArrayList<>();
        for (StockBatch candidate : candidates) {
            StockBatch locked = stockBatchRepository.findByIdForUpdate(candidate.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
            lockedBatches.add(locked);
        }
        int currentQuantity = lockedBatches.stream().mapToInt(StockBatch::getQuantity).sum();
        if (currentQuantity != item.getExpectedQuantity()) {
            throw new ResourceConflictException(ErrorCode.AUDIT_STOCK_CHANGED);
        }

        int delta = item.getActualQuantity() - item.getExpectedQuantity();
        item.setDiscrepancy(delta);
        if (delta == 0) {
            return;
        }
        if (delta > 0) {
            StockBatch newBatch = stockBatchRepository.save(StockBatch.builder()
                    .skuId(skuId).warehouse(audit.getWarehouse()).rack(item.getRack())
                    .bin(item.getBin()).quantity(0).arrivalDate(LocalDateTime.now()).build());
            InventoryReceipt receipt = inventoryReceiptService.createAuditAdjustmentReceipt(
                    userId, auditId, audit.getWarehouse().getId(), DocumentType.INBOUND,
                    newBatch.getId(), delta);
            auditAdjustmentRepository.save(InventoryAuditAdjustment.builder()
                    .audit(audit).auditItem(item).batch(newBatch).receipt(receipt).delta(delta).build());
            item.setBatch(newBatch);
            return;
        }

        int remaining = -delta;
        for (StockBatch batch : lockedBatches) {
            if (remaining == 0) {
                break;
            }
            int allocated = Math.min(batch.getQuantity(), remaining);
            if (allocated <= 0) {
                continue;
            }
            if (!auditAdjustmentRepository.existsByAuditItemIdAndBatchId(item.getId(), batch.getId())) {
                InventoryReceipt receipt = inventoryReceiptService.createAuditAdjustmentReceipt(
                        userId, auditId, audit.getWarehouse().getId(), DocumentType.OUTBOUND,
                        batch.getId(), allocated);
                auditAdjustmentRepository.save(InventoryAuditAdjustment.builder()
                        .audit(audit).auditItem(item).batch(batch).receipt(receipt).delta(-allocated).build());
            }
            remaining -= allocated;
        }
        if (remaining > 0) {
            throw new ResourceConflictException(ErrorCode.AUDIT_STOCK_CHANGED);
        }
    }

    private boolean sameLocation(StockBatch batch, InventoryAuditItem item) {
        return sameId(batch.getRack(), item.getRack()) && sameId(batch.getBin(), item.getBin());
    }

    private boolean sameId(Object left, Object right) {
        UUID leftId = left instanceof WarehouseRack rack ? rack.getId()
                : left instanceof WarehouseBin bin ? bin.getId() : null;
        UUID rightId = right instanceof WarehouseRack rack ? rack.getId()
                : right instanceof WarehouseBin bin ? bin.getId() : null;
        return leftId == null ? rightId == null : leftId.equals(rightId);
    }

    private Comparator<StockBatch> fifoComparator() {
        return Comparator.comparing(StockBatch::getArrivalDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockBatch::getId);
    }

    private UUID idOf(WarehouseRack rack) {
        return rack == null ? null : rack.getId();
    }

    private UUID idOf(WarehouseBin bin) {
        return bin == null ? null : bin.getId();
    }

    private record BatchScopeKey(UUID skuId, UUID rackId, UUID binId) {
    }

    private UUID resolveAuditTenantId(InventoryAudit audit) {
        return audit.getTenant() != null ? audit.getTenant().getId() : resolveTenantId(audit.getRequestedBy().getId());
    }

    private void validateScopeRequest(CreateInventoryAuditPlanRequest request) {
        AuditScopeType scope = request.getScopeType() == null ? AuditScopeType.WAREHOUSE : request.getScopeType();
        if (scope == AuditScopeType.WAREHOUSE && (request.getRackId() != null || request.getBinId() != null)) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (scope == AuditScopeType.RACK && (request.getRackId() == null || request.getBinId() != null)) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
        if (scope == AuditScopeType.BIN && request.getBinId() == null) {
            throw new BadRequestException(ErrorCode.AUDIT_SCOPE_INVALID);
        }
    }

    private boolean inScope(InventoryAudit audit, StockBatch batch) {
        if (audit.getScopeType() == AuditScopeType.WAREHOUSE) {
            return true;
        }
        if (audit.getScopeType() == AuditScopeType.RACK) {
            return audit.getScopeRack() != null && batch.getRack() != null
                    && audit.getScopeRack().getId().equals(batch.getRack().getId());
        }
        return audit.getScopeBin() != null && batch.getBin() != null
                && audit.getScopeBin().getId().equals(batch.getBin().getId());
    }

    private void requireV2ReadAccess(InventoryAudit audit, User actor) {
        UUID tenantId = resolveTenantId(actor.getId());
        if (!tenantId.equals(resolveAuditTenantId(audit))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (isStaff(actor) && audit.getAssignedTo() != null
                && !actor.getId().equals(audit.getAssignedTo().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        requireWarehouseObservationAccess(actor, tenantId, audit.getWarehouse().getId());
    }

    private void requireV2CountAccess(InventoryAudit audit, User actor) {
        requireV2ReadAccess(audit, actor);
        if (isStaff(actor) && (audit.getAssignedTo() == null
                || !actor.getId().equals(audit.getAssignedTo().getId()))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireV2TenantReviewer(InventoryAudit audit, User actor) {
        ensureApproverIsTenant(actor);
        if (!resolveTenantId(actor.getId()).equals(resolveAuditTenantId(audit))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        requireWarehouseMutationAccess(actor, resolveAuditTenantId(audit), audit.getWarehouse().getId());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private void pushAuditNotification(UUID recipientId, String title, String message) {
        try {
            notificationService.push(recipientId, title, message, "AUDIT");
        } catch (Exception e) {
            log.warn("Failed to push v2 audit notification: {}", e.getMessage());
        }
    }

    private InventoryAuditResponse maskCounterResponse(InventoryAuditResponse response, User actor) {
        if (actor != null && isStaff(actor) && response.getItems() != null) {
            response.getItems().forEach(item -> {
                item.setExpectedQuantity(null);
                item.setDiscrepancy(null);
            });
        }
        return response;
    }
}
