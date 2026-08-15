package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
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
    private final SubscriptionService subscriptionService;
    private final TenantMemberRepository tenantMemberRepository;








    @Transactional
    public InventoryAuditResponse createAudit(UUID userId, CreateInventoryAuditRequest request) {
        checkSubscription(userId);

        User requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        InventoryAudit audit = InventoryAudit.builder()
                .warehouse(warehouse)
                .requestedBy(requestedBy)
                .status(AuditStatus.PENDING)
                .note(request.getNote())
                .build();
        audit = auditRepository.save(audit);


        List<StockBatch> batches = stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(
                warehouse.getId(), Pageable.unpaged()).getContent();

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

        log.info("InventoryAudit: Created audit {} for warehouse {} ({} batch lines snapshotted)",
                audit.getId(), warehouse.getId(), items.size());
        return mapToResponse(audit, items);
    }





    @Transactional
    public InventoryAuditResponse submitAudit(UUID userId, UUID auditId, SubmitAuditRequest request) {
        InventoryAudit audit = getAuditForUser(auditId, userId);

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


        List<InventoryAuditItem> updatedItems = auditItemRepository.findByAuditId(auditId);
        log.info("InventoryAudit: Audit {} submitted by user {}", auditId, userId);
        return mapToResponse(audit, updatedItems);
    }






    @Transactional
    public InventoryAuditResponse approveAudit(UUID approverId, UUID auditId) {
        InventoryAudit audit = auditRepository.findById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));

        if (audit.getStatus() != AuditStatus.SUBMITTED) {
            throw new BadRequestException(ErrorCode.AUDIT_INVALID_STATUS);
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ensureApproverIsTenant(approver);

        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);
        UUID warehouseId = audit.getWarehouse().getId();

        for (InventoryAuditItem item : items) {
            if (item.getDiscrepancy() == null || item.getDiscrepancy() == 0) continue;

            int absDiscrepancy = Math.abs(item.getDiscrepancy());
            DocumentType type = item.getDiscrepancy() > 0 ? DocumentType.INBOUND : DocumentType.OUTBOUND;


            inventoryReceiptService.createAdjustmentReceipt(
                    approverId, auditId, warehouseId,
                    type, item.getBatch().getId(), absDiscrepancy
            );
        }

        audit.setApprovedBy(approver);
        audit.setStatus(AuditStatus.APPROVED);
        audit = auditRepository.save(audit);


        String warehouseName = audit.getWarehouse().getName();
        notificationService.push(
                audit.getRequestedBy().getId(),
                "Phiếu kiểm kê đã được duyệt",
                "Phiếu kiểm kê kho " + warehouseName + " đã được duyệt. Tồn kho đã được điều chỉnh tự động.",
                "AUDIT"
        );

        log.info("InventoryAudit: Audit {} approved by user {}", auditId, approverId);
        return mapToResponse(audit, items);
    }




    @Transactional
    public InventoryAuditResponse rejectAudit(UUID approverId, UUID auditId, String reason) {
        InventoryAudit audit = auditRepository.findById(auditId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUDIT_NOT_FOUND));

        if (audit.getStatus() != AuditStatus.SUBMITTED && audit.getStatus() != AuditStatus.PENDING) {
            throw new BadRequestException(ErrorCode.AUDIT_ALREADY_PROCESSED);
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ensureApproverIsTenant(approver);

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
                "Phiếu kiểm kê kho " + warehouseName + " bị từ chối. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể"),
                "AUDIT"
        );

        log.info("InventoryAudit: Audit {} rejected by user {} (reason: {})", auditId, approverId, reason);
        List<InventoryAuditItem> items = auditItemRepository.findByAuditId(auditId);
        return mapToResponse(audit, items);
    }




    @Transactional(readOnly = true)
    public PagedResponse<InventoryAuditResponse> getMyAudits(UUID userId, Pageable pageable) {
        Page<InventoryAudit> page = auditRepository.findByRequestedByIdAndIsDeletedFalse(userId, pageable);
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

        boolean isRequester = audit.getRequestedBy().getId().equals(userId);
        boolean isApprover = audit.getApprovedBy() != null && audit.getApprovedBy().getId().equals(userId);
        if (!isRequester && !isApprover) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return audit;
    }

    private void checkSubscription(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId)
                .map(member -> member.getTenant().getId())
                .orElse(userId);
        if (!subscriptionService.hasActiveSubscription(tenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }




    private void ensureApproverIsTenant(User approver) {
        boolean isTenant = approver.getRoles() != null && approver.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_TENANT.name().equals(role.getName()));
        if (!isTenant) {
            throw new ForbiddenException(
                    "Chỉ Doanh nghiệp (Tenant) có quyền duyệt hoặc từ chối phiếu kiểm kê."
            );
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
