package fu.stockspace.stockspace_be.wms.receipt.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoad;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
import fu.stockspace.stockspace_be.wms.receipt.dto.*;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryTransactionResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingInputItem;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingSuggestionService;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickLineResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickStopResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReceiptService {

    private final InventoryReceiptRepository receiptRepository;
    private final InventoryReceiptItemRepository receiptItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockBatchRepository stockBatchRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ProductSkuRepository productSkuRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final NotificationService notificationService;
    private final PhysicalLoadCalculator physicalLoadCalculator;
    private final OutboundPickingSuggestionService pickingSuggestionService;

    @Transactional
    public InventoryReceiptResponse createReceipt(UUID userId, CreateInventoryReceiptRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        UUID tenantId = resolveTenantId(creator);

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseMutationAccess(creator, tenantId, warehouse.getId());

        if (request.getType() == DocumentType.OUTBOUND) {
            return createOutboundReceipt(creator, tenantId, warehouse, request);
        }

        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .createdBy(creator)
                .type(request.getType())
                .signatureData(request.getSignatureData())
                .senderName(request.getSenderName())
                .receiverName(request.getReceiverName())
                .status(ApprovalStatus.PENDING)
                .build();

        receipt = receiptRepository.save(receipt);

        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        List<CapacityItem> capacityItems = new ArrayList<>();
        for (ReceiptItemRequest itemRequest : request.getItems()) {
            ProductSku sku = productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(
                            itemRequest.getSkuId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

            if (itemRequest.getRackId() == null) {
                throw new BadRequestException("Rack is required for inbound receipts");
            }
            if (itemRequest.getBinId() == null) {
                throw new BadRequestException("Bin is required for inbound receipts");
            }

            WarehouseRack rack = rackRepository.findByIdAndIsDeletedFalse(itemRequest.getRackId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            if (!rack.getLayout().getWarehouse().getId().equals(warehouse.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(itemRequest.getBinId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            if (!bin.getRack().getId().equals(rack.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            if (request.getType() == DocumentType.INBOUND) {
                capacityItems.add(new CapacityItem(bin, rack, sku, itemRequest.getQuantity()));
            }

            InventoryReceiptItem item = InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .sku(sku)
                    .quantity(itemRequest.getQuantity())
                    .rack(rack)
                    .bin(bin)
                    .note(itemRequest.getNote())
                    .build();

            savedItems.add(receiptItemRepository.save(item));
        }

        if (request.getType() == DocumentType.INBOUND) {
            validateInboundCapacity(tenantId, warehouse.getId(), capacityItems, false);
        }

        notifyReceiptCreated(receipt, creator, tenantId, warehouse);

        log.info("WMS Receipt: Created receipt {} of type {} for warehouse {}", receipt.getId(), receipt.getType(), warehouse.getId());
        return mapToResponse(receipt, savedItems);
    }

    private InventoryReceiptResponse createOutboundReceipt(
            User creator, UUID tenantId, Warehouse warehouse, CreateInventoryReceiptRequest request) {
        for (ReceiptItemRequest itemRequest : request.getItems()) {
            if (itemRequest.getRackId() != null || itemRequest.getBinId() != null) {
                throw new BadRequestException("Rack and bin must be omitted for outbound receipts");
            }
        }

        List<OutboundPickingInputItem> inputItems = request.getItems().stream()
                .map(item -> new OutboundPickingInputItem(item.getSkuId(), item.getQuantity()))
                .toList();
        OutboundPickingSuggestionResponse pickList = pickingSuggestionService.suggest(
                tenantId,
                isStaff(creator) ? creator.getId() : null,
                warehouse.getId(),
                inputItems);
        if (!pickList.complete()) {
            throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
        }

        Map<UUID, ProductSku> skusById = new LinkedHashMap<>();
        Map<UUID, String> notesBySkuId = new HashMap<>();
        for (ReceiptItemRequest itemRequest : request.getItems()) {
            ProductSku sku = productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(
                            itemRequest.getSkuId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));
            skusById.put(itemRequest.getSkuId(), sku);
            notesBySkuId.put(itemRequest.getSkuId(), itemRequest.getNote());
        }

        Map<UUID, StockBatch> batchesById = loadPickListBatches(pickList);
        validateOutboundAllocations(pickList, batchesById, skusById, warehouse);

        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .createdBy(creator)
                .type(request.getType())
                .signatureData(request.getSignatureData())
                .senderName(request.getSenderName())
                .receiverName(request.getReceiverName())
                .status(ApprovalStatus.PENDING)
                .build();
        receipt = receiptRepository.save(receipt);

        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        for (InventoryReceiptItem item : buildOutboundReceiptItems(
                receipt, pickList, batchesById, skusById, notesBySkuId)) {
            savedItems.add(receiptItemRepository.save(item));
        }

        notifyReceiptCreated(receipt, creator, tenantId, warehouse);

        log.info("WMS Receipt: Created receipt {} of type {} for warehouse {}", receipt.getId(), receipt.getType(), warehouse.getId());
        return mapToResponse(receipt, savedItems, pickList);
    }

    @Transactional
    public InventoryReceiptResponse replanOutboundReceipt(UUID userId, UUID receiptId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(actor);
        InventoryReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));

        if (receipt.getType() != DocumentType.OUTBOUND) {
            throw new BadRequestException("Only outbound receipts can be replanned");
        }
        if (receipt.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.RECEIPT_ALREADY_PROCESSED);
        }
        requireWarehouseMutationAccess(actor, tenantId, receipt.getWarehouse().getId());

        List<InventoryReceiptItem> currentItems = receiptItemRepository.findByReceiptId(receiptId);
        Map<UUID, Integer> requestedQuantities = new LinkedHashMap<>();
        Map<UUID, String> notesBySkuId = new LinkedHashMap<>();
        for (InventoryReceiptItem item : currentItems) {
            if (item == null || item.getSku() == null || item.getSku().getId() == null
                    || item.getQuantity() <= 0) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }
            UUID skuId = item.getSku().getId();
            try {
                requestedQuantities.merge(skuId, item.getQuantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }
            if (!notesBySkuId.containsKey(skuId) || notesBySkuId.get(skuId) == null) {
                notesBySkuId.put(skuId, item.getNote());
            }
        }
        if (requestedQuantities.isEmpty()) {
            throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
        }

        List<OutboundPickingInputItem> inputItems = requestedQuantities.entrySet().stream()
                .map(entry -> new OutboundPickingInputItem(entry.getKey(), entry.getValue()))
                .toList();
        OutboundPickingSuggestionResponse pickList = pickingSuggestionService.suggest(
                tenantId,
                isStaff(actor) ? actor.getId() : null,
                receipt.getWarehouse().getId(),
                inputItems);
        if (!pickList.complete()) {
            throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
        }

        Map<UUID, ProductSku> skusById = new LinkedHashMap<>();
        for (UUID skuId : requestedQuantities.keySet()) {
            ProductSku sku = productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));
            skusById.put(skuId, sku);
        }
        Map<UUID, StockBatch> batchesById = loadPickListBatches(pickList);
        validateOutboundAllocations(pickList, batchesById, skusById, receipt.getWarehouse());

        List<InventoryReceiptItem> replacementItems = buildOutboundReceiptItems(
                receipt, pickList, batchesById, skusById, notesBySkuId);
        receiptItemRepository.deleteAll(currentItems);
        List<InventoryReceiptItem> savedItems = receiptItemRepository.saveAll(replacementItems);

        log.info("WMS Receipt: Replanned outbound receipt {} by user {}", receiptId, userId);
        return mapToResponse(receipt, savedItems, pickList);
    }

    private Map<UUID, StockBatch> loadPickListBatches(OutboundPickingSuggestionResponse pickList) {
        Set<UUID> batchIds = pickList.stops().stream()
                .flatMap(stop -> stop.lines().stream())
                .map(OutboundPickLineResponse::stockBatchId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return stockBatchRepository.findAllById(batchIds).stream()
                .collect(Collectors.toMap(StockBatch::getId, batch -> batch, (left, right) -> left));
    }

    private List<InventoryReceiptItem> buildOutboundReceiptItems(
            InventoryReceipt receipt,
            OutboundPickingSuggestionResponse pickList,
            Map<UUID, StockBatch> batchesById,
            Map<UUID, ProductSku> skusById,
            Map<UUID, String> notesBySkuId) {
        List<InventoryReceiptItem> items = new ArrayList<>();
        for (OutboundPickStopResponse stop : pickList.stops()) {
            for (OutboundPickLineResponse line : stop.lines()) {
                StockBatch batch = batchesById.get(line.stockBatchId());
                items.add(InventoryReceiptItem.builder()
                        .receipt(receipt)
                        .sku(skusById.get(line.skuId()))
                        .quantity(line.quantity())
                        .rack(batch.getRack())
                        .bin(batch.getBin())
                        .stockBatch(batch)
                        .pickSequence(stop.sequence())
                        .note(notesBySkuId.get(line.skuId()))
                        .build());
            }
        }
        return items;
    }

    private void validateOutboundAllocations(
            OutboundPickingSuggestionResponse pickList,
            Map<UUID, StockBatch> batchesById,
            Map<UUID, ProductSku> skusById,
            Warehouse warehouse) {
        Set<UUID> expectedBatchIds = pickList.stops().stream()
                .flatMap(stop -> stop.lines().stream())
                .map(OutboundPickLineResponse::stockBatchId)
                .collect(Collectors.toSet());
        if (batchesById.size() != expectedBatchIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND);
        }

        for (OutboundPickStopResponse stop : pickList.stops()) {
            for (OutboundPickLineResponse line : stop.lines()) {
                StockBatch batch = batchesById.get(line.stockBatchId());
                ProductSku sku = skusById.get(line.skuId());
                if (sku == null || batch == null || batch.getSkuId() == null
                        || !line.skuId().equals(batch.getSkuId())
                        || !batch.isActive() || batch.isDeleted()
                        || batch.getQuantity() < line.quantity()
                        || batch.getWarehouse() == null
                        || !warehouse.getId().equals(batch.getWarehouse().getId())
                        || batch.getRack() == null || batch.getBin() == null) {
                    throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
                }
            }
        }
    }

    private void notifyReceiptCreated(
            InventoryReceipt receipt, User creator, UUID tenantId, Warehouse warehouse) {
        try {
            String typeStr = receipt.getType() == DocumentType.INBOUND ? "nhập kho" : "xuất kho";
            if (isStaff(creator)) {
                notificationService.push(
                        tenantId,
                        "Yêu cầu duyệt phiếu " + typeStr,
                        "Nhân viên " + creator.getFullName() + " đã tạo phiếu " + typeStr + " mới tại kho " + warehouse.getName() + " và đang chờ bạn phê duyệt.",
                        "RECEIPT"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to push create notification for receipt {}: {}", receipt.getId(), e.getMessage());
        }
    }

    private Map<UUID, StockBatch> lockAndValidateOutboundBatches(
            InventoryReceipt receipt, List<InventoryReceiptItem> items) {
        Map<UUID, Long> requestedByBatchId = new HashMap<>();
        for (InventoryReceiptItem item : items) {
            if (item == null || item.getStockBatch() == null
                    || item.getStockBatch().getId() == null
                    || item.getSku() == null || item.getRack() == null || item.getBin() == null
                    || item.getPickSequence() == null || item.getPickSequence() <= 0
                    || item.getQuantity() <= 0) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }

            UUID batchId = item.getStockBatch().getId();
            try {
                requestedByBatchId.merge(batchId, (long) item.getQuantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }
        }

        Map<UUID, StockBatch> lockedBatches = new LinkedHashMap<>();
        requestedByBatchId.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(batchId -> {
                    StockBatch batch = stockBatchRepository.findByIdForUpdate(batchId)
                            .orElseThrow(() -> new ResourceConflictException(
                                    ErrorCode.OUTBOUND_PICK_LIST_STALE));
                    lockedBatches.put(batchId, batch);
                });

        UUID warehouseId = receipt.getWarehouse() != null ? receipt.getWarehouse().getId() : null;
        for (InventoryReceiptItem item : items) {
            UUID batchId = item.getStockBatch().getId();
            StockBatch batch = lockedBatches.get(batchId);
            if (batch == null
                    || !batchId.equals(batch.getId())
                    || !batch.isActive() || batch.isDeleted()
                    || batch.getSkuId() == null || item.getSku().getId() == null
                    || !item.getSku().getId().equals(batch.getSkuId())
                    || warehouseId == null || batch.getWarehouse() == null
                    || !warehouseId.equals(batch.getWarehouse().getId())
                    || item.getRack().getId() == null || batch.getRack() == null
                    || batch.getRack().getId() == null
                    || !item.getRack().getId().equals(batch.getRack().getId())
                    || item.getBin().getId() == null || batch.getBin() == null
                    || batch.getBin().getId() == null
                    || !item.getBin().getId().equals(batch.getBin().getId())) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }
            item.setStockBatch(batch);
        }

        for (Map.Entry<UUID, Long> entry : requestedByBatchId.entrySet()) {
            StockBatch batch = lockedBatches.get(entry.getKey());
            if (batch == null || batch.getQuantity() < entry.getValue()) {
                throw new ResourceConflictException(ErrorCode.OUTBOUND_PICK_LIST_STALE);
            }
        }
        return lockedBatches;
    }

    @Transactional
    public InventoryReceiptResponse approveReceipt(UUID approverId, UUID receiptId) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        boolean isStaff = approver.getRoles() != null && approver.getRoles().stream()
                .anyMatch(r -> RoleType.ROLE_STAFF.name().equals(r.getName()));
        if (isStaff) {
            throw new ForbiddenException("Nhân viên không có quyền phê duyệt phiếu nhập/xuất kho. Phiếu phải được Doanh nghiệp (Tenant) phê duyệt.");
        }

        InventoryReceipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));

        if (receipt.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.RECEIPT_ALREADY_PROCESSED);
        }


        UUID creatorTenantId = resolveTenantId(receipt.getCreatedBy());
        UUID approverTenantId = resolveTenantId(approver);
        if (!creatorTenantId.equals(approverTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        requireWarehouseMutationAccess(approver, approverTenantId, receipt.getWarehouse().getId());

        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);

        Map<UUID, StockBatch> lockedOutboundBatches = Map.of();
        if (receipt.getType() == DocumentType.OUTBOUND) {
            lockedOutboundBatches = lockAndValidateOutboundBatches(receipt, items);
        }

        if (receipt.getType() == DocumentType.INBOUND) {
            validateInboundCapacity(approverTenantId, receipt.getWarehouse().getId(), items.stream()
                    .map(item -> new CapacityItem(item.getBin(), item.getRack(), item.getSku(), item.getQuantity()))
                    .toList(), true);
        }

        for (InventoryReceiptItem item : items) {
            UUID skuId = item.getSku().getId();

            if (receipt.getType() == DocumentType.INBOUND) {
                StockBatch batch = StockBatch.builder()
                        .skuId(skuId)
                        .warehouse(receipt.getWarehouse())
                        .rack(item.getRack())
                        .bin(item.getBin())
                        .quantity(item.getQuantity())
                        .arrivalDate(LocalDateTime.now())
                        .build();
                batch = stockBatchRepository.save(batch);

                item.setStockBatch(batch);
                receiptItemRepository.save(item);

                InventoryTransaction transaction = InventoryTransaction.builder()
                        .receipt(receipt)
                        .batch(batch)
                        .quantityChanged(item.getQuantity())
                        .build();
                transactionRepository.save(transaction);

            } else if (receipt.getType() == DocumentType.OUTBOUND) {
                StockBatch batch = lockedOutboundBatches.get(item.getStockBatch().getId());

                batch.setQuantity(batch.getQuantity() - item.getQuantity());
                stockBatchRepository.save(batch);

                InventoryTransaction transaction = InventoryTransaction.builder()
                        .receipt(receipt)
                        .batch(batch)
                        .quantityChanged(-item.getQuantity())
                        .build();
                transactionRepository.save(transaction);
            }
        }

        receipt.setStatus(ApprovalStatus.APPROVED);
        receipt = receiptRepository.save(receipt);

        try {
            String typeStr = receipt.getType() == DocumentType.INBOUND ? "nhập kho" : "xuất kho";
            notificationService.push(
                    receipt.getCreatedBy().getId(),
                    "Phiếu " + typeStr + " đã được phê duyệt",
                    "Phiếu " + typeStr + " tại kho " + receipt.getWarehouse().getName() + " đã được phê duyệt thành công. Hàng hóa trong kho đã được cập nhật.",
                    "RECEIPT"
            );
        } catch (Exception e) {
            log.warn("Failed to push approve notification for receipt {}: {}", receipt.getId(), e.getMessage());
        }

        log.info("WMS Receipt: Approved receipt {} of type {} by user {}", receipt.getId(), receipt.getType(), approverId);
        return mapToResponse(receipt, items);
    }

    @Transactional
    public InventoryReceiptResponse rejectReceipt(UUID approverId, UUID receiptId, String reason) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (isStaff(approver)) {
            throw new ForbiddenException("Nhân viên không có quyền từ chối phiếu nhập/xuất kho. Phiếu phải được Doanh nghiệp (Tenant) phê duyệt.");
        }

        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));

        if (receipt.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.RECEIPT_ALREADY_PROCESSED);
        }

        UUID creatorTenantId = resolveTenantId(receipt.getCreatedBy());
        UUID approverTenantId = resolveTenantId(approver);
        if (!creatorTenantId.equals(approverTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        requireWarehouseMutationAccess(approver, approverTenantId, receipt.getWarehouse().getId());

        receipt.setStatus(ApprovalStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            receipt.setRejectReason(reason);
        }
        receipt = receiptRepository.save(receipt);

        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);

        try {
            String typeStr = receipt.getType() == DocumentType.INBOUND ? "nhập kho" : "xuất kho";
            notificationService.push(
                    receipt.getCreatedBy().getId(),
                    "Phiếu " + typeStr + " bị từ chối",
                    "Phiếu " + typeStr + " tại kho " + receipt.getWarehouse().getName() + " đã bị từ chối. Lý do: " + (reason != null && !reason.isBlank() ? reason : "Không có lý do cụ thể"),
                    "RECEIPT"
            );
        } catch (Exception e) {
            log.warn("Failed to push reject notification for receipt {}: {}", receiptId, e.getMessage());
        }

        log.info("WMS Receipt: Rejected receipt {} of type {} by user {} (reason: {})", receipt.getId(), receipt.getType(), approverId, reason);
        return mapToResponse(receipt, items);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InventoryReceiptResponse> getReceiptsByWarehouse(UUID warehouseId, DocumentType type, Pageable pageable) {
        Page<InventoryReceipt> page;
        if (type != null) {
            page = receiptRepository.findByWarehouseIdAndTypeAndIsDeletedFalse(warehouseId, type, pageable);
        } else {
            page = receiptRepository.findByWarehouseIdAndIsDeletedFalse(warehouseId, pageable);
        }

        return PagedResponse.fromPage(page, receipt -> {
            List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receipt.getId());
            return mapToResponse(receipt, items);
        });
    }




    @Transactional(readOnly = true)
    public PagedResponse<InventoryReceiptResponse> getReceiptsByWarehouse(
            UUID userId, UUID warehouseId, DocumentType type, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(user);
        requireWarehouseObservationAccess(user, tenantId, warehouseId);
        return getReceiptsByWarehouse(warehouseId, type, pageable);
    }


    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptDetail(UUID receiptId) {
        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));
        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);
        return mapToResponse(receipt, items);
    }




    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptDetail(UUID userId, UUID receiptId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(user);
        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));
        requireWarehouseObservationAccess(user, tenantId, receipt.getWarehouse().getId());
        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);
        return mapToResponse(receipt, items);
    }

    private InventoryReceiptResponse mapToResponse(InventoryReceipt receipt, List<InventoryReceiptItem> items) {
        return mapToResponse(receipt, items, null);
    }

    private InventoryReceiptResponse mapToResponse(
            InventoryReceipt receipt,
            List<InventoryReceiptItem> items,
            OutboundPickingSuggestionResponse pickList) {
        List<ReceiptItemResponse> itemResponses = items.stream().map(item -> ReceiptItemResponse.builder()
                .id(item.getId())
                .skuId(item.getSku().getId())
                .skuCode(item.getSku().getSkuCode())
                .skuName(item.getSku().getName())
                .quantity(item.getQuantity())
                .rackId(item.getRack().getId())
                .rackName(item.getRack().getName())
                .binId(item.getBin().getId())
                .binName(item.getBin().getName())
                .stockBatchId(item.getStockBatch() != null ? item.getStockBatch().getId() : null)
                .pickSequence(item.getPickSequence())
                .note(item.getNote())
                .build()).collect(Collectors.toList());

        return InventoryReceiptResponse.builder()
                .id(receipt.getId())
                .warehouseId(receipt.getWarehouse().getId())
                .warehouseName(receipt.getWarehouse().getName())
                .createdById(receipt.getCreatedBy().getId())
                .createdByFullName(receipt.getCreatedBy().getFullName())
                .type(receipt.getType())
                .signatureData(receipt.getSignatureData())
                .senderName(receipt.getSenderName())
                .receiverName(receipt.getReceiverName())
                .status(receipt.getStatus())
                .rejectReason(receipt.getRejectReason())
                .items(itemResponses)
                .pickList(pickList)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }







    @Transactional
    public InventoryReceipt createAdjustmentReceipt(
            UUID userId, UUID auditId, UUID warehouseId,
            DocumentType type, UUID batchId, int quantity) {

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        UUID tenantId = resolveTenantId(creator);
        requireWarehouseMutationAccess(creator, tenantId, warehouseId);
        if (quantity <= 0) {
            throw new BadRequestException("Adjustment quantity must be greater than 0");
        }

        StockBatch batch = stockBatchRepository.findByIdAndIsDeletedFalse(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(batch.getSkuId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        int delta = (type == DocumentType.INBOUND) ? quantity : -quantity;
        if (batch.getQuantity() + delta < 0) {
            throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
        }
        if (type == DocumentType.INBOUND) {
            validateInboundCapacity(tenantId, warehouseId,
                    List.of(new CapacityItem(batch.getBin(), batch.getRack(), sku, quantity)), true);
        }


        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .createdBy(creator)
                .type(type)
                .signatureData(null)
                .status(ApprovalStatus.APPROVED)
                .referenceId(auditId)
                .build();
        receipt = receiptRepository.save(receipt);


        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .receipt(receipt)
                .sku(sku)
                .quantity(quantity)
                .rack(batch.getRack())
                .bin(batch.getBin())
                .stockBatch(batch)
                .note("Điều chỉnh tự động từ kiểm kê #" + auditId)
                .build();
        receiptItemRepository.save(item);


        batch.setQuantity(batch.getQuantity() + delta);
        stockBatchRepository.save(batch);


        InventoryTransaction transaction = InventoryTransaction.builder()
                .receipt(receipt)
                .batch(batch)
                .quantityChanged(delta)
                .build();
        transactionRepository.save(transaction);

        log.info("WMS AdjustmentReceipt: Created {} receipt for audit {} (batch={}, qty={})",
                type, auditId, batchId, quantity);
        return receipt;
    }




    @Transactional(readOnly = true)
    public byte[] exportReceiptsToCsv(UUID warehouseId, DocumentType type) {
        Page<InventoryReceipt> page;
        if (type != null) {
            page = receiptRepository.findByWarehouseIdAndTypeAndIsDeletedFalse(warehouseId, type, Pageable.unpaged());
        } else {
            page = receiptRepository.findByWarehouseIdAndIsDeletedFalse(warehouseId, Pageable.unpaged());
        }

        StringBuilder csv = new StringBuilder();
        csv.append("sep=,\n");
        csv.append("\uFEFF");
        csv.append("STT,Mã Phiếu,Loại Phiếu,Kho Bãi,Tên Nơi Gửi,Tên Nơi Nhận,Trạng Thái,Mã SKU,Tên Sản Phẩm,Đơn Vị Tính,Số Lượng,Người Tạo,Thời Gian Tạo\n");

        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int stt = 1;

        for (InventoryReceipt receipt : page.getContent()) {
            List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receipt.getId());
            String warehouseName = escapeCsvField(receipt.getWarehouse() != null ? receipt.getWarehouse().getName() : "");
            String senderName = escapeCsvField(receipt.getSenderName());
            String receiverName = escapeCsvField(receipt.getReceiverName());
            String typeStr = receipt.getType() == DocumentType.INBOUND ? "Nhập kho" : "Xuất kho";
            String statusStr = mapStatusToVietnamese(receipt.getStatus());
            String createdByStr = escapeCsvField(receipt.getCreatedBy() != null ? receipt.getCreatedBy().getFullName() : "");
            String formattedDate = receipt.getCreatedAt() != null ? receipt.getCreatedAt().format(dateFormatter) : "";

            if (items.isEmpty()) {
                csv.append(String.format("%d,%s,\"%s\",\"%s\",\"%s\",\"%s\",%s,-,-,-,0,\"%s\",%s\n",
                        stt++, receipt.getId(), typeStr, warehouseName, senderName, receiverName,
                        statusStr, createdByStr, formattedDate));
            } else {
                for (InventoryReceiptItem item : items) {
                    ProductSku sku = item.getSku();
                    String skuCode = sku != null ? sku.getSkuCode() : "-";
                    String skuName = escapeCsvField(sku != null ? sku.getName() : "-");
                    String uomName = sku != null && sku.getUom() != null ? sku.getUom().getName() : "-";

                    csv.append(String.format("%d,%s,%s,\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",%s,%d,\"%s\",%s\n",
                            stt++, receipt.getId(), typeStr, warehouseName, senderName, receiverName,
                            statusStr, skuCode, skuName, uomName, item.getQuantity(), createdByStr, formattedDate));
                }
            }
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }




    @Transactional(readOnly = true)
    public byte[] exportReceiptsToCsv(UUID userId, UUID warehouseId, DocumentType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(user);
        requireWarehouseObservationAccess(user, tenantId, warehouseId);
        return exportReceiptsToCsv(warehouseId, type);
    }

    private String mapStatusToVietnamese(ApprovalStatus status) {
        if (status == null) return "Chờ duyệt";
        return switch (status) {
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Từ chối";
            default -> "Chờ duyệt";
        };
    }

    private String escapeCsvField(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"");
    }






    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InventoryTransactionResponse> getTransactionsByBatch(
            UUID batchId, org.springframework.data.domain.Pageable pageable) {
        return transactionRepository.findByBatchId(batchId, pageable)
                .map(t -> {
                    ProductSku sku = productSkuRepository
                            .findByIdAndIsDeletedFalse(t.getBatch().getSkuId()).orElse(null);
                    return InventoryTransactionResponse.builder()
                            .id(t.getId())
                            .receiptId(t.getReceipt().getId())
                            .batchId(t.getBatch().getId())
                            .skuCode(sku != null ? sku.getSkuCode() : null)
                            .skuName(sku != null ? sku.getName() : null)
                            .quantityChanged(t.getQuantityChanged())
                            .createdAt(t.getCreatedAt())
                            .build();
                });
    }




    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InventoryTransactionResponse> getTransactionsByBatch(
            UUID userId, UUID batchId, org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        UUID tenantId = resolveTenantId(user);
        StockBatch batch = stockBatchRepository.findByIdAndIsDeletedFalse(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        requireWarehouseObservationAccess(user, tenantId, batch.getWarehouse().getId());
        return getTransactionsByBatch(batchId, pageable);
    }

    private void validateInboundCapacity(UUID tenantId, UUID warehouseId,
                                         List<CapacityItem> items, boolean lockLocations) {
        if (items == null || items.isEmpty()) return;

        items.forEach(item -> {
            if (item.quantity() <= 0) {
                throw new BadRequestException("Inbound quantity must be greater than 0");
            }
        });

        Map<UUID, WarehouseRack> racks = new HashMap<>();
        Map<UUID, WarehouseBin> bins = new HashMap<>();
        for (CapacityItem item : items) {
            WarehouseRack rack = item.rack() != null
                    ? item.rack()
                    : item.bin() != null ? item.bin().getRack() : null;
            if (rack != null && rack.getId() != null) racks.put(rack.getId(), rack);
            if (item.bin() != null && item.bin().getId() != null) bins.put(item.bin().getId(), item.bin());
        }

        if (lockLocations) {
            racks.keySet().stream().sorted(UUID::compareTo).forEach(rackId ->
                    Optional.ofNullable(rackRepository.findByIdForUpdate(rackId))
                            .flatMap(optional -> optional)
                            .ifPresent(rack -> racks.put(rackId, rack)));
            bins.keySet().stream().sorted(UUID::compareTo).forEach(binId ->
                    Optional.ofNullable(binRepository.findByIdForUpdate(binId))
                            .flatMap(optional -> optional)
                            .ifPresent(bin -> bins.put(binId, bin)));
        }

        boolean hasLimitedLocation = racks.values().stream()
                .anyMatch(rack -> physicalLoadCalculator.isLimited(rack.getMaxWeight())
                        || physicalLoadCalculator.isLimited(rack.getMaxVolume()))
                || bins.values().stream()
                .anyMatch(bin -> physicalLoadCalculator.isLimited(bin.getMaxWeight())
                        || physicalLoadCalculator.isLimited(bin.getMaxVolume()));
        List<PhysicalLoadLine> currentLoads = hasLimitedLocation
                ? stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId)
                : List.of();
        Map<UUID, List<PhysicalLoadLine>> currentLoadsByRack = currentLoads.stream()
                .filter(line -> line.rackId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::rackId));
        Map<UUID, List<PhysicalLoadLine>> currentLoadsByBin = currentLoads.stream()
                .filter(line -> line.binId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::binId));
        List<PhysicalLoadLine> incomingLoads = items.stream()
                .map(this::toPhysicalLoadLine)
                .toList();
        Map<UUID, List<PhysicalLoadLine>> incomingLoadsByRack = incomingLoads.stream()
                .filter(line -> line.rackId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::rackId));
        Map<UUID, List<PhysicalLoadLine>> incomingLoadsByBin = incomingLoads.stream()
                .filter(line -> line.binId() != null)
                .collect(Collectors.groupingBy(PhysicalLoadLine::binId));

        for (WarehouseRack rack : racks.values()) {
            boolean weightLimited = physicalLoadCalculator.isLimited(rack.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(rack.getMaxVolume());
            if (!weightLimited && !volumeLimited) continue;

            List<PhysicalLoadLine> lines = new ArrayList<>(
                    currentLoadsByRack.getOrDefault(rack.getId(), List.of()));
            lines.addAll(incomingLoadsByRack.getOrDefault(rack.getId(), List.of()));
            PhysicalLoad total = physicalLoadCalculator.calculate(lines, weightLimited, volumeLimited);
            physicalLoadCalculator.assertWithinCapacity("rack", rack.getName(),
                    rack.getMaxWeight(), rack.getMaxVolume(), total);
        }

        for (WarehouseBin bin : bins.values()) {
            boolean weightLimited = physicalLoadCalculator.isLimited(bin.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(bin.getMaxVolume());
            if (!weightLimited && !volumeLimited) continue;

            List<PhysicalLoadLine> lines = new ArrayList<>(
                    currentLoadsByBin.getOrDefault(bin.getId(), List.of()));
            lines.addAll(incomingLoadsByBin.getOrDefault(bin.getId(), List.of()));
            PhysicalLoad total = physicalLoadCalculator.calculate(lines, weightLimited, volumeLimited);
            physicalLoadCalculator.assertWithinCapacity("bin", bin.getName(),
                    bin.getMaxWeight(), bin.getMaxVolume(), total);
        }
    }

    private PhysicalLoadLine toPhysicalLoadLine(CapacityItem item) {
        WarehouseRack rack = item.rack() != null
                ? item.rack()
                : item.bin() != null ? item.bin().getRack() : null;
        ProductSku sku = item.sku();
        return new PhysicalLoadLine(
                rack != null ? rack.getId() : null,
                item.bin() != null ? item.bin().getId() : null,
                sku.getId(),
                sku.getSkuCode(),
                sku.getName(),
                sku.getUnitWeightKg(),
                sku.getUnitVolumeM3(),
                item.quantity());
    }

    private record CapacityItem(WarehouseBin bin, WarehouseRack rack, ProductSku sku, int quantity) {
    }

    private UUID resolveTenantId(User user) {
        if (isStaff(user)) {
            return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(user.getId())
                    .map(member -> member.getTenant().getId())
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
        }
        return user.getId();
    }

    private void requireWarehouseObservationAccess(User user, UUID tenantId, UUID warehouseId) {
        accessService.requireActiveContract(tenantId, warehouseId);
        if (isStaff(user)) {
            accessService.requireActiveStaffAssignment(user.getId(), tenantId, warehouseId);
        }
    }

    private void requireWarehouseMutationAccess(User user, UUID tenantId, UUID warehouseId) {
        requireWarehouseObservationAccess(user, tenantId, warehouseId);
        accessService.requireActiveSubscription(tenantId);
    }

    private boolean isStaff(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName()));
    }
}
