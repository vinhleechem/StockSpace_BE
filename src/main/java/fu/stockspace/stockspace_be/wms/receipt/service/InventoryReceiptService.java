package fu.stockspace.stockspace_be.wms.receipt.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseZone;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseZoneRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.dto.*;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final WarehouseZoneRepository zoneRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public InventoryReceiptResponse createReceipt(UUID userId, CreateInventoryReceiptRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // Subscription check based on creator's tenant
        UUID tenantId = creator.getTenant() != null ? creator.getTenant().getId() : creator.getId();
        if (!subscriptionService.hasActiveSubscription(tenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .createdBy(creator)
                .type(request.getType())
                .signatureData(request.getSignatureData())
                .status(ApprovalStatus.PENDING)
                .build();

        receipt = receiptRepository.save(receipt);

        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        for (ReceiptItemRequest itemRequest : request.getItems()) {
            ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(itemRequest.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

            WarehouseZone zone = zoneRepository.findById(itemRequest.getZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ZONE_NOT_FOUND));
            if (!zone.getLayout().getWarehouse().getId().equals(warehouse.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            WarehouseRack rack = rackRepository.findById(itemRequest.getRackId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            if (!rack.getZone().getId().equals(zone.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            WarehouseBin bin = binRepository.findById(itemRequest.getBinId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            if (!bin.getRack().getId().equals(rack.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            InventoryReceiptItem item = InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .sku(sku)
                    .quantity(itemRequest.getQuantity())
                    .zone(zone)
                    .rack(rack)
                    .bin(bin)
                    .note(itemRequest.getNote())
                    .build();

            savedItems.add(receiptItemRepository.save(item));
        }

        log.info("WMS Receipt: Created receipt {} of type {} for warehouse {}", receipt.getId(), receipt.getType(), warehouse.getId());
        return mapToResponse(receipt, savedItems);
    }

    @Transactional
    public InventoryReceiptResponse approveReceipt(UUID approverId, UUID receiptId) {
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));

        if (receipt.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.RECEIPT_ALREADY_PROCESSED);
        }

        // Check active subscription of creator's tenant
        UUID creatorTenantId = receipt.getCreatedBy().getTenant() != null ? receipt.getCreatedBy().getTenant().getId() : receipt.getCreatedBy().getId();
        if (!subscriptionService.hasActiveSubscription(creatorTenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }

        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);

        for (InventoryReceiptItem item : items) {
            UUID skuId = item.getSku().getId();
            UUID warehouseId = receipt.getWarehouse().getId();
            UUID zoneId = item.getZone().getId();
            UUID rackId = item.getRack().getId();
            UUID binId = item.getBin().getId();

            if (receipt.getType() == DocumentType.INBOUND) {
                StockBatch batch = stockBatchRepository
                        .findBySkuIdAndWarehouseIdAndZoneIdAndRackIdAndBinIdAndIsDeletedFalse(skuId, warehouseId, zoneId, rackId, binId)
                        .orElse(null);

                if (batch != null) {
                    batch.setQuantity(batch.getQuantity() + item.getQuantity());
                    stockBatchRepository.save(batch);
                } else {
                    batch = StockBatch.builder()
                            .skuId(skuId)
                            .warehouse(receipt.getWarehouse())
                            .zone(item.getZone())
                            .rack(item.getRack())
                            .bin(item.getBin())
                            .quantity(item.getQuantity())
                            .arrivalDate(LocalDateTime.now())
                            .build();
                    stockBatchRepository.save(batch);
                }

                InventoryTransaction transaction = InventoryTransaction.builder()
                        .receipt(receipt)
                        .batch(batch)
                        .quantityChanged(item.getQuantity())
                        .build();
                transactionRepository.save(transaction);

            } else if (receipt.getType() == DocumentType.OUTBOUND) {
                StockBatch batch = stockBatchRepository
                        .findBySkuIdAndWarehouseIdAndZoneIdAndRackIdAndBinIdAndIsDeletedFalse(skuId, warehouseId, zoneId, rackId, binId)
                        .orElseThrow(() -> new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY));

                if (batch.getQuantity() < item.getQuantity()) {
                    throw new BadRequestException(ErrorCode.STOCK_INSUFFICIENT_QUANTITY);
                }

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

        log.info("WMS Receipt: Approved receipt {} of type {} by user {}", receipt.getId(), receipt.getType(), approverId);
        return mapToResponse(receipt, items);
    }

    @Transactional(readOnly = true)
    public PagedReceiptResponse getReceiptsByWarehouse(UUID warehouseId, DocumentType type, Pageable pageable) {
        Page<InventoryReceipt> page;
        if (type != null) {
            page = receiptRepository.findByWarehouseIdAndTypeAndIsDeletedFalse(warehouseId, type, pageable);
        } else {
            page = receiptRepository.findByWarehouseIdAndIsDeletedFalse(warehouseId, pageable);
        }

        List<InventoryReceiptResponse> content = page.getContent().stream()
                .map(receipt -> {
                    List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receipt.getId());
                    return mapToResponse(receipt, items);
                })
                .collect(Collectors.toList());

        return PagedReceiptResponse.builder()
                .content(content)
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptDetail(UUID receiptId) {
        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));
        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);
        return mapToResponse(receipt, items);
    }

    private InventoryReceiptResponse mapToResponse(InventoryReceipt receipt, List<InventoryReceiptItem> items) {
        List<ReceiptItemResponse> itemResponses = items.stream().map(item -> ReceiptItemResponse.builder()
                .id(item.getId())
                .skuId(item.getSku().getId())
                .skuCode(item.getSku().getSkuCode())
                .skuName(item.getSku().getName())
                .quantity(item.getQuantity())
                .zoneId(item.getZone().getId())
                .zoneName(item.getZone().getName())
                .rackId(item.getRack().getId())
                .rackName(item.getRack().getName())
                .binId(item.getBin().getId())
                .binName(item.getBin().getName())
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
                .status(receipt.getStatus())
                .items(itemResponses)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}
