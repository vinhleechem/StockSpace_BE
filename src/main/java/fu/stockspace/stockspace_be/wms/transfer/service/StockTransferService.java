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
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoad;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
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
import fu.stockspace.stockspace_be.wms.transfer.dto.ReceiveStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDecisionRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private static final String TRANSFER_NOTIFICATION_TYPE = "TRANSFER";

    private final StockTransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final UserRepository userRepository;
    private final ProductSkuRepository productSkuRepository;
    private final StockBatchRepository stockBatchRepository;
    private final InventoryReceiptRepository receiptRepository;
    private final InventoryReceiptItemRepository receiptItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final PhysicalLoadCalculator physicalLoadCalculator;
    private final NotificationService notificationService;

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

        StockTransfer savedTransfer = transferRepository.save(transfer);
        notifyTransferCreated(savedTransfer, creator, tenantId);
        return mapToResponse(savedTransfer);
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
                .receiverName(transfer.getDestinationWarehouse().getName())
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
                    .stockBatch(batch)
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
        StockTransfer savedTransfer = transferRepository.save(transfer);
        notifyTransferCreator(
                savedTransfer,
                "Yêu cầu chuyển kho đã được duyệt xuất",
                transferRoute(savedTransfer) + " đã được duyệt xuất và đang vận chuyển.",
                "dispatch");
        return mapToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse receiveTransfer(UUID userId, UUID transferId,
                                                 ReceiveStockTransferRequest request) {
        User receiver = findUser(userId);
        if (isStaff(receiver) || !hasRole(receiver, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        UUID tenantId = resolveTenantId(receiver);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        WarehouseLayout destinationLayout = findActiveTenantLayout(
                transfer.getDestinationWarehouse().getId(), tenantId);
        List<DestinationAllocationReference> references = validateDestinationAllocations(
                transfer, request, destinationLayout);
        Map<UUID, WarehouseRack> lockedRacks = lockDestinationRacks(references, destinationLayout);
        Map<UUID, WarehouseBin> lockedBins = lockDestinationBins(references, lockedRacks, destinationLayout);
        validateDestinationCapacity(tenantId, transfer, references, lockedRacks, lockedBins);

        InventoryReceipt inboundReceipt = receiptRepository.save(InventoryReceipt.builder()
                .warehouse(transfer.getDestinationWarehouse())
                .createdBy(receiver)
                .type(DocumentType.INBOUND)
                .senderName(transfer.getSourceWarehouse().getName())
                .status(ApprovalStatus.APPROVED)
                .referenceId(transfer.getId())
                .build());

        for (DestinationAllocationReference reference : references) {
            StockTransferItem item = reference.item();
            StockTransferDestinationAllocationRequest allocationRequest = reference.request();
            WarehouseRack rack = lockedRacks.get(allocationRequest.getDestinationRackId());
            WarehouseBin bin = lockedBins.get(allocationRequest.getDestinationBinId());

            StockBatch batch = stockBatchRepository.save(StockBatch.builder()
                    .skuId(item.getSku().getId())
                    .warehouse(transfer.getDestinationWarehouse())
                    .rack(rack)
                    .bin(bin)
                    .quantity(allocationRequest.getQuantity())
                    .arrivalDate(java.time.LocalDateTime.now())
                    .build());

            item.getDestinationAllocations().add(StockTransferDestinationAllocation.builder()
                    .item(item)
                    .destinationRack(rack)
                    .destinationBin(bin)
                    .quantity(allocationRequest.getQuantity())
                    .build());
            receiptItemRepository.save(InventoryReceiptItem.builder()
                    .receipt(inboundReceipt)
                    .sku(item.getSku())
                    .quantity(allocationRequest.getQuantity())
                    .rack(rack)
                    .bin(bin)
                    .stockBatch(batch)
                    .build());
            transactionRepository.save(InventoryTransaction.builder()
                    .receipt(inboundReceipt)
                    .batch(batch)
                    .quantityChanged(allocationRequest.getQuantity())
                    .build());
        }

        transfer.setReceivedBy(receiver);
        transfer.setReceivedAt(java.time.LocalDateTime.now());
        transfer.setInboundReceipt(inboundReceipt);
        transfer.setStatus(StockTransferStatus.COMPLETED);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        notifyTransferCreator(
                savedTransfer,
                "Chuyển kho đã hoàn tất",
                transferRoute(savedTransfer)
                        + " đã được tiếp nhận thành công. Tồn kho tại kho đích đã được cập nhật.",
                "receive");
        return mapToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID userId, UUID transferId,
                                                StockTransferDecisionRequest request) {
        return decidePendingTransfer(userId, transferId, request, StockTransferStatus.REJECTED);
    }

    @Transactional
    public StockTransferResponse cancelTransfer(UUID userId, UUID transferId,
                                                StockTransferDecisionRequest request) {
        return decidePendingTransfer(userId, transferId, request, StockTransferStatus.CANCELLED);
    }

    private StockTransferResponse decidePendingTransfer(UUID userId, UUID transferId,
                                                        StockTransferDecisionRequest request,
                                                        StockTransferStatus decision) {
        User actor = findUser(userId);
        if (isStaff(actor) || !hasRole(actor, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        String reason = normalizeDecisionReason(request);
        transfer.setDecisionReason(reason);
        if (decision == StockTransferStatus.REJECTED) {
            transfer.setRejectedBy(actor);
            transfer.setRejectedAt(java.time.LocalDateTime.now());
        } else {
            transfer.setCancelledBy(actor);
            transfer.setCancelledAt(java.time.LocalDateTime.now());
        }
        transfer.setStatus(decision);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        if (decision == StockTransferStatus.REJECTED) {
            notifyTransferCreator(
                    savedTransfer,
                    "Yêu cầu chuyển kho bị từ chối",
                    transferRoute(savedTransfer) + " đã bị từ chối. Lý do: " + reason,
                    "reject");
        } else {
            notifyTransferCreator(
                    savedTransfer,
                    "Yêu cầu chuyển kho đã bị hủy",
                    transferRoute(savedTransfer) + " đã bị hủy. Lý do: " + reason,
                    "cancel");
        }
        return mapToResponse(savedTransfer);
    }

    private void notifyTransferCreated(StockTransfer transfer, User creator, UUID tenantId) {
        if (!isStaff(creator)) {
            return;
        }

        String creatorName = displayName(creator);
        notifySafely(
                tenantId,
                "Yêu cầu chuyển kho mới",
                "Nhân viên " + creatorName + " đã tạo " + transferRoute(transfer)
                        + " và đang chờ bạn duyệt xuất.",
                "create",
                transfer.getId());
    }

    private void notifyTransferCreator(StockTransfer transfer, String title, String message, String action) {
        if (transfer.getCreatedBy() == null || transfer.getCreatedBy().getId() == null) {
            log.warn("Cannot push {} notification for transfer {} because creator is missing",
                    action, transfer.getId());
            return;
        }
        notifySafely(transfer.getCreatedBy().getId(), title, message, action, transfer.getId());
    }

    private void notifySafely(UUID recipientId, String title, String message,
                              String action, UUID transferId) {
        try {
            notificationService.push(recipientId, title, message, TRANSFER_NOTIFICATION_TYPE);
        } catch (Exception exception) {
            log.warn("Failed to push {} notification for transfer {}: {}",
                    action, transferId, exception.getMessage());
        }
    }

    private String transferRoute(StockTransfer transfer) {
        return "yêu cầu chuyển kho từ kho '" + transfer.getSourceWarehouse().getName()
                + "' đến kho '" + transfer.getDestinationWarehouse().getName() + "'";
    }

    private String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getId().toString();
    }

    private String normalizeDecisionReason(StockTransferDecisionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_DECISION_REASON_REQUIRED);
        }
        return request.getReason().trim();
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

    private WarehouseLayout findActiveTenantLayout(UUID warehouseId, UUID tenantId) {
        return layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(layout -> layout.isActive() && !layout.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));
    }

    private List<DestinationAllocationReference> validateDestinationAllocations(
            StockTransfer transfer, ReceiveStockTransferRequest request, WarehouseLayout layout) {
        Map<UUID, StockTransferItem> itemsById = transfer.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(StockTransferItem::getId, item -> item));
        Map<UUID, Long> quantitiesByItem = new LinkedHashMap<>();
        Set<DestinationLocationKey> locations = new HashSet<>();
        List<DestinationAllocationReference> references = new ArrayList<>();

        for (StockTransferDestinationAllocationRequest allocationRequest : request.getDestinationAllocations()) {
            StockTransferItem item = itemsById.get(allocationRequest.getItemId());
            if (item == null || !locations.add(new DestinationLocationKey(
                    allocationRequest.getItemId(), allocationRequest.getDestinationRackId(),
                    allocationRequest.getDestinationBinId()))) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Destination allocation không thuộc transfer hoặc bị lặp");
            }
            WarehouseRack rack = rackRepository.findByIdAndIsDeletedFalse(
                            allocationRequest.getDestinationRackId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(
                            allocationRequest.getDestinationBinId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            if (!rack.isActive() || !bin.isActive()
                    || rack.getLayout() == null || !layout.getId().equals(rack.getLayout().getId())
                    || bin.getRack() == null || !rack.getId().equals(bin.getRack().getId())) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Rack/bin đích không thuộc layout của warehouse đích");
            }
            quantitiesByItem.merge(item.getId(), (long) allocationRequest.getQuantity(), Long::sum);
            references.add(new DestinationAllocationReference(item, allocationRequest, rack, bin));
        }

        for (StockTransferItem item : transfer.getItems()) {
            if (!item.getDestinationAllocations().isEmpty()
                    || quantitiesByItem.getOrDefault(item.getId(), 0L) != item.getRequestedQuantity()) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Tổng phân bổ đích phải bằng requestedQuantity của từng SKU");
            }
        }
        references.sort(Comparator
                .comparing((DestinationAllocationReference reference) -> reference.request()
                        .getDestinationRackId())
                .thenComparing(reference -> reference.request().getDestinationBinId())
                .thenComparing(reference -> reference.item().getId()));
        return references;
    }

    private Map<UUID, WarehouseRack> lockDestinationRacks(
            List<DestinationAllocationReference> references, WarehouseLayout layout) {
        Set<UUID> rackIds = references.stream()
                .map(reference -> reference.request().getDestinationRackId())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Map<UUID, WarehouseRack> locked = new LinkedHashMap<>();
        for (UUID rackId : rackIds) {
            WarehouseRack rack = rackRepository.findByIdForUpdate(rackId)
                    .filter(candidate -> candidate.isActive()
                            && candidate.getLayout() != null
                            && layout.getId().equals(candidate.getLayout().getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            locked.put(rackId, rack);
        }
        return locked;
    }

    private Map<UUID, WarehouseBin> lockDestinationBins(
            List<DestinationAllocationReference> references,
            Map<UUID, WarehouseRack> lockedRacks,
            WarehouseLayout layout) {
        Set<UUID> binIds = references.stream()
                .map(reference -> reference.request().getDestinationBinId())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Map<UUID, WarehouseBin> locked = new LinkedHashMap<>();
        for (UUID binId : binIds) {
            WarehouseBin bin = binRepository.findByIdForUpdate(binId)
                    .filter(candidate -> candidate.isActive()
                            && candidate.getRack() != null
                            && lockedRacks.containsKey(candidate.getRack().getId())
                            && candidate.getRack().getLayout() != null
                            && layout.getId().equals(candidate.getRack().getLayout().getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            locked.put(binId, bin);
        }
        return locked;
    }

    private void validateDestinationCapacity(UUID tenantId, StockTransfer transfer,
                                             List<DestinationAllocationReference> references,
                                             Map<UUID, WarehouseRack> racks,
                                             Map<UUID, WarehouseBin> bins) {
        List<PhysicalLoadLine> currentLoads = stockBatchRepository
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(
                        transfer.getDestinationWarehouse().getId(), tenantId);
        Map<UUID, List<PhysicalLoadLine>> currentByRack = currentLoads.stream()
                .filter(line -> line.rackId() != null)
                .collect(java.util.stream.Collectors.groupingBy(PhysicalLoadLine::rackId));
        Map<UUID, List<PhysicalLoadLine>> currentByBin = currentLoads.stream()
                .filter(line -> line.binId() != null)
                .collect(java.util.stream.Collectors.groupingBy(PhysicalLoadLine::binId));
        Map<UUID, List<PhysicalLoadLine>> incomingByRack = new LinkedHashMap<>();
        Map<UUID, List<PhysicalLoadLine>> incomingByBin = new LinkedHashMap<>();
        for (DestinationAllocationReference reference : references) {
            ProductSku sku = reference.item().getSku();
            StockTransferDestinationAllocationRequest request = reference.request();
            PhysicalLoadLine line = new PhysicalLoadLine(
                    request.getDestinationRackId(), request.getDestinationBinId(), sku.getId(),
                    sku.getSkuCode(), sku.getName(), sku.getUnitWeightKg(), sku.getUnitVolumeM3(),
                    request.getQuantity());
            incomingByRack.computeIfAbsent(request.getDestinationRackId(), ignored -> new ArrayList<>())
                    .add(line);
            incomingByBin.computeIfAbsent(request.getDestinationBinId(), ignored -> new ArrayList<>())
                    .add(line);
        }

        boolean hasLimitedRack = incomingByRack.keySet().stream()
                .map(racks::get)
                .anyMatch(rack -> physicalLoadCalculator.isLimited(rack.getMaxWeight())
                        || physicalLoadCalculator.isLimited(rack.getMaxVolume()));
        boolean hasLimitedBin = incomingByBin.keySet().stream()
                .map(bins::get)
                .anyMatch(bin -> physicalLoadCalculator.isLimited(bin.getMaxWeight())
                        || physicalLoadCalculator.isLimited(bin.getMaxVolume()));
        if (!hasLimitedRack && !hasLimitedBin) {
            return;
        }

        for (UUID rackId : incomingByRack.keySet()) {
            WarehouseRack rack = racks.get(rackId);
            boolean weightLimited = physicalLoadCalculator.isLimited(rack.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(rack.getMaxVolume());
            if (!weightLimited && !volumeLimited) {
                continue;
            }
            List<PhysicalLoadLine> lines = new ArrayList<>(currentByRack.getOrDefault(rackId, List.of()));
            lines.addAll(incomingByRack.get(rackId));
            PhysicalLoad load = physicalLoadCalculator.calculate(lines, weightLimited, volumeLimited);
            physicalLoadCalculator.assertWithinCapacity(
                    "rack", rack.getName(), rack.getMaxWeight(), rack.getMaxVolume(), load);
        }
        for (UUID binId : incomingByBin.keySet()) {
            WarehouseBin bin = bins.get(binId);
            boolean weightLimited = physicalLoadCalculator.isLimited(bin.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(bin.getMaxVolume());
            if (!weightLimited && !volumeLimited) {
                continue;
            }
            List<PhysicalLoadLine> lines = new ArrayList<>(currentByBin.getOrDefault(binId, List.of()));
            lines.addAll(incomingByBin.get(binId));
            PhysicalLoad load = physicalLoadCalculator.calculate(lines, weightLimited, volumeLimited);
            physicalLoadCalculator.assertWithinCapacity(
                    "bin", bin.getName(), bin.getMaxWeight(), bin.getMaxVolume(), load);
        }
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
        accessService.requireActiveStaffAssignment(staffId, tenantId, sourceWarehouseId);
        accessService.requireActiveStaffAssignment(staffId, tenantId, destinationWarehouseId);
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

    private record DestinationAllocationReference(StockTransferItem item,
                                                  StockTransferDestinationAllocationRequest request,
                                                  WarehouseRack rack,
                                                  WarehouseBin bin) {
    }

    private record DestinationLocationKey(UUID itemId, UUID rackId, UUID binId) {
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
