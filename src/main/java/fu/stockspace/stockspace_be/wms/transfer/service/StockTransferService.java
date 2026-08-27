package fu.stockspace.stockspace_be.wms.transfer.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferSourceAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferSourceAllocationResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.TransferActorResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.WarehouseSummaryResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferDestinationAllocation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferItem;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferSourceAllocation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ProductSkuRepository productSkuRepository;
    private final StockBatchRepository stockBatchRepository;
    private final InventoryReceiptRepository receiptRepository;
    private final InventoryReceiptItemRepository receiptItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final StaffWarehouseAssignmentRepository assignmentRepository;

    @Transactional
    public StockTransferResponse createTransfer(UUID userId, CreateStockTransferRequest request) {
        User creator = findUser(userId);
        UUID tenantId = resolveTenantId(creator);

        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_SOURCE_DESTINATION_SAME);
        }

        Warehouse sourceWarehouse = findActiveWarehouse(request.getSourceWarehouseId());
        Warehouse destinationWarehouse = findActiveWarehouse(request.getDestinationWarehouseId());
        requireMutationAccess(creator, tenantId, sourceWarehouse.getId(), destinationWarehouse.getId());

        Set<UUID> skuIds = new HashSet<>();
        StockTransfer transfer = StockTransfer.builder()
                .tenant(tenantUser(tenantId))
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .createdBy(creator)
                .note(request.getNote())
                .build();

        for (StockTransferItemRequest itemRequest : request.getItems()) {
            if (!skuIds.add(itemRequest.getSkuId())) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Mỗi SKU chỉ được xuất hiện một lần trong một yêu cầu chuyển kho");
            }

            ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(itemRequest.getSkuId())
                    .filter(candidate -> candidate.isActive()
                            && candidate.getTenant() != null
                            && tenantId.equals(candidate.getTenant().getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

            StockTransferItem transferItem = StockTransferItem.builder()
                    .transfer(transfer)
                    .sku(sku)
                    .requestedQuantity(itemRequest.getRequestedQuantity())
                    .build();

            long allocatedQuantity = 0;
            Set<UUID> batchIds = new HashSet<>();
            for (StockTransferSourceAllocationRequest allocationRequest : itemRequest.getSourceAllocations()) {
                if (!batchIds.add(allocationRequest.getSourceStockBatchId())) {
                    throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                            "Không được lặp stock batch trong cùng một SKU");
                }

                StockBatch batch = stockBatchRepository.findByIdAndIsDeletedFalse(
                                allocationRequest.getSourceStockBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
                validateSourceAllocation(batch, sku, sourceWarehouse, allocationRequest);
                allocatedQuantity += allocationRequest.getQuantity();

                StockTransferSourceAllocation allocation = StockTransferSourceAllocation.builder()
                        .item(transferItem)
                        .sourceStockBatch(batch)
                        .sourceRack(batch.getRack())
                        .sourceBin(batch.getBin())
                        .quantity(allocationRequest.getQuantity())
                        .build();
                transferItem.getSourceAllocations().add(allocation);
            }

            if (allocatedQuantity != itemRequest.getRequestedQuantity()) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Tổng phân bổ nguồn phải bằng requestedQuantity của SKU");
            }
            transfer.getItems().add(transferItem);
        }

        return mapToResponse(transferRepository.save(transfer));
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockTransferResponse> getTransfers(UUID userId,
                                                              UUID sourceWarehouseId,
                                                              UUID destinationWarehouseId,
                                                              StockTransferStatus status,
                                                              Pageable pageable) {
        User user = findUser(userId);
        UUID tenantId = resolveTenantId(user);
        Page<StockTransfer> page = transferRepository.search(
                tenantId, sourceWarehouseId, destinationWarehouseId, status,
                isStaff(user) ? user.getId() : null, pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID userId, UUID transferId) {
        User user = findUser(userId);
        UUID tenantId = resolveTenantId(user);
        StockTransfer transfer = transferRepository.findByIdAndTenantIdAndIsDeletedFalse(transferId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        if (isStaff(user)) {
            requireStaffAssignments(user.getId(), tenantId,
                    transfer.getSourceWarehouse().getId(), transfer.getDestinationWarehouse().getId());
        }
        return mapToResponse(transfer);
    }

    @Transactional
    public StockTransferResponse approveDispatch(UUID userId, UUID transferId) {
        User approver = findUser(userId);
        if (isStaff(approver) || !hasRole(approver, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        UUID tenantId = resolveTenantId(approver);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        List<LockedSourceAllocation> lockedAllocations = lockAndValidateSourceAllocations(transfer);

        InventoryReceipt outboundReceipt = receiptRepository.save(InventoryReceipt.builder()
                .warehouse(transfer.getSourceWarehouse())
                .createdBy(approver)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.APPROVED)
                .referenceId(transfer.getId())
                .build());

        for (LockedSourceAllocation locked : lockedAllocations) {
            StockTransferSourceAllocation allocation = locked.allocation();
            StockTransferItem item = locked.item();
            StockBatch batch = locked.batch();
            batch.setQuantity(batch.getQuantity() - allocation.getQuantity());
            stockBatchRepository.save(batch);

            InventoryReceiptItem receiptItem = receiptItemRepository.save(InventoryReceiptItem.builder()
                    .receipt(outboundReceipt)
                    .sku(item.getSku())
                    .quantity(allocation.getQuantity())
                    .rack(allocation.getSourceRack())
                    .bin(allocation.getSourceBin())
                    .build());
            transactionRepository.save(InventoryTransaction.builder()
                    .receipt(outboundReceipt)
                    .batch(batch)
                    .quantityChanged(-allocation.getQuantity())
                    .build());
        }

        transfer.setApprovedBy(approver);
        transfer.setApprovedAt(java.time.LocalDateTime.now());
        transfer.setOutboundReceipt(outboundReceipt);
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        return mapToResponse(transferRepository.save(transfer));
    }

    private void validateSourceAllocation(StockBatch batch, ProductSku sku,
                                          Warehouse sourceWarehouse,
                                          StockTransferSourceAllocationRequest request) {
        if (!batch.isActive() || batch.getWarehouse() == null
                || !sourceWarehouse.getId().equals(batch.getWarehouse().getId())
                || !sku.getId().equals(batch.getSkuId())
                || batch.getRack() == null || batch.getBin() == null
                || !batch.getRack().getId().equals(request.getSourceRackId())
                || !batch.getBin().getId().equals(request.getSourceBinId())
                || batch.getQuantity() < request.getQuantity()) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Stock batch nguồn không khớp warehouse, SKU, vị trí hoặc số lượng hiện tại");
        }
    }

    private void requireMutationAccess(User user, UUID tenantId,
                                       UUID sourceWarehouseId, UUID destinationWarehouseId) {
        accessService.requireActiveContract(tenantId, sourceWarehouseId);
        accessService.requireActiveContract(tenantId, destinationWarehouseId);
        accessService.requireActiveSubscription(tenantId);
        if (isStaff(user)) {
            requireStaffAssignments(user.getId(), tenantId, sourceWarehouseId, destinationWarehouseId);
        }
    }

    private void requireTenantMutationAccess(UUID tenantId, StockTransfer transfer) {
        accessService.requireActiveContract(tenantId, transfer.getSourceWarehouse().getId());
        accessService.requireActiveContract(tenantId, transfer.getDestinationWarehouse().getId());
        accessService.requireActiveSubscription(tenantId);
    }

    private List<LockedSourceAllocation> lockAndValidateSourceAllocations(StockTransfer transfer) {
        List<SourceAllocationReference> references = new ArrayList<>();
        for (StockTransferItem item : transfer.getItems()) {
            for (StockTransferSourceAllocation allocation : item.getSourceAllocations()) {
                references.add(new SourceAllocationReference(item, allocation));
            }
        }
        references.sort(Comparator.comparing(reference -> reference.allocation()
                .getSourceStockBatch().getId()));

        List<LockedSourceAllocation> lockedAllocations = new ArrayList<>();
        Set<UUID> lockedBatchIds = new HashSet<>();
        for (SourceAllocationReference reference : references) {
            StockTransferSourceAllocation allocation = reference.allocation();
            UUID batchId = allocation.getSourceStockBatch().getId();
            if (!lockedBatchIds.add(batchId)) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Một source stock batch không được phân bổ lặp lại");
            }

            StockBatch batch = stockBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
            if (batch.getWarehouse() == null
                    || !transfer.getSourceWarehouse().getId().equals(batch.getWarehouse().getId())
                    || !reference.item().getSku().getId().equals(batch.getSkuId())
                    || batch.getRack() == null || batch.getBin() == null
                    || allocation.getSourceRack() == null || allocation.getSourceBin() == null
                    || !allocation.getSourceRack().getId().equals(batch.getRack().getId())
                    || !allocation.getSourceBin().getId().equals(batch.getBin().getId())
                    || batch.getQuantity() < allocation.getQuantity()) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Source stock batch không còn khớp vị trí hoặc không đủ số lượng");
            }
            lockedAllocations.add(new LockedSourceAllocation(reference.item(), allocation, batch));
        }
        return lockedAllocations;
    }

    private void requireStaffAssignments(UUID staffId, UUID tenantId,
                                         UUID sourceWarehouseId, UUID destinationWarehouseId) {
        boolean assignedToSource = assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, sourceWarehouseId, AssignmentStatus.ACTIVE);
        boolean assignedToDestination = assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, destinationWarehouseId, AssignmentStatus.ACTIVE);
        if (!assignedToSource || !assignedToDestination) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .filter(warehouse -> warehouse.isActive() && !warehouse.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private User tenantUser(UUID tenantId) {
        return userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TENANT_NOT_FOUND));
    }

    private UUID resolveTenantId(User user) {
        if (isStaff(user)) {
            return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(user.getId())
                    .map(member -> member.getTenant().getId())
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
        }
        if (hasRole(user, RoleType.ROLE_TENANT)) {
            return user.getId();
        }
        throw new ForbiddenException(ErrorCode.FORBIDDEN);
    }

    private boolean isStaff(User user) {
        return hasRole(user, RoleType.ROLE_STAFF);
    }

    private boolean hasRole(User user, RoleType roleType) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> roleType.name().equals(role.getName()));
    }

    private record SourceAllocationReference(StockTransferItem item,
                                             StockTransferSourceAllocation allocation) {
    }

    private record LockedSourceAllocation(StockTransferItem item,
                                          StockTransferSourceAllocation allocation,
                                          StockBatch batch) {
    }

    private StockTransferResponse mapToResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .status(transfer.getStatus())
                .sourceWarehouse(warehouseSummary(transfer.getSourceWarehouse()))
                .destinationWarehouse(warehouseSummary(transfer.getDestinationWarehouse()))
                .note(transfer.getNote())
                .items(transfer.getItems().stream().map(this::mapItem).toList())
                .createdBy(actor(transfer.getCreatedBy()))
                .approvedBy(actor(transfer.getApprovedBy()))
                .receivedBy(actor(transfer.getReceivedBy()))
                .rejectedBy(actor(transfer.getRejectedBy()))
                .cancelledBy(actor(transfer.getCancelledBy()))
                .decisionReason(transfer.getDecisionReason())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .approvedAt(transfer.getApprovedAt())
                .receivedAt(transfer.getReceivedAt())
                .rejectedAt(transfer.getRejectedAt())
                .cancelledAt(transfer.getCancelledAt())
                .outboundReceiptId(transfer.getOutboundReceipt() == null
                        ? null : transfer.getOutboundReceipt().getId())
                .inboundReceiptId(transfer.getInboundReceipt() == null
                        ? null : transfer.getInboundReceipt().getId())
                .build();
    }

    private StockTransferItemResponse mapItem(StockTransferItem item) {
        ProductSku sku = item.getSku();
        return StockTransferItemResponse.builder()
                .id(item.getId())
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .skuName(sku.getName())
                .requestedQuantity(item.getRequestedQuantity())
                .sourceAllocations(item.getSourceAllocations().stream()
                        .map(this::mapSourceAllocation).toList())
                .destinationAllocations(item.getDestinationAllocations().stream()
                        .map(this::mapDestinationAllocation).toList())
                .build();
    }

    private StockTransferSourceAllocationResponse mapSourceAllocation(StockTransferSourceAllocation allocation) {
        WarehouseRack rack = allocation.getSourceRack();
        WarehouseBin bin = allocation.getSourceBin();
        return StockTransferSourceAllocationResponse.builder()
                .id(allocation.getId())
                .sourceStockBatchId(allocation.getSourceStockBatch().getId())
                .sourceRackId(rack.getId())
                .sourceRackName(rack.getName())
                .sourceBinId(bin.getId())
                .sourceBinName(bin.getName())
                .quantity(allocation.getQuantity())
                .build();
    }

    private StockTransferDestinationAllocationResponse mapDestinationAllocation(
            StockTransferDestinationAllocation allocation) {
        WarehouseRack rack = allocation.getDestinationRack();
        WarehouseBin bin = allocation.getDestinationBin();
        return StockTransferDestinationAllocationResponse.builder()
                .id(allocation.getId())
                .destinationRackId(rack.getId())
                .destinationRackName(rack.getName())
                .destinationBinId(bin.getId())
                .destinationBinName(bin.getName())
                .quantity(allocation.getQuantity())
                .build();
    }

    private WarehouseSummaryResponse warehouseSummary(Warehouse warehouse) {
        return warehouse == null ? null : WarehouseSummaryResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .build();
    }

    private TransferActorResponse actor(User user) {
        return user == null ? null : TransferActorResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .build();
    }
}
