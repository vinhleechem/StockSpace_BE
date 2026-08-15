package fu.stockspace.stockspace_be.wms.receipt.service;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
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
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final SubscriptionService subscriptionService;
    private final fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;
    private final RentalContractRepository rentalContractRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;

    @Transactional
    public InventoryReceiptResponse createReceipt(UUID userId, CreateInventoryReceiptRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        UUID tenantId = resolveTenantId(creator);

        if (!subscriptionService.hasActiveSubscription(tenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        requireWarehouseAccess(creator, tenantId, warehouse.getId());

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
            ProductSku sku = productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(
                            itemRequest.getSkuId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

            WarehouseRack rack = rackRepository.findById(itemRequest.getRackId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
            if (!rack.getLayout().getWarehouse().getId().equals(warehouse.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            WarehouseBin bin = binRepository.findById(itemRequest.getBinId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
            if (!bin.getRack().getId().equals(rack.getId())) {
                throw new BadRequestException(ErrorCode.LAYOUT_INVALID_COORDINATES);
            }

            if (request.getType() == DocumentType.INBOUND) {
                validateBinCapacity(bin, itemRequest.getQuantity());
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

        log.info("WMS Receipt: Created receipt {} of type {} for warehouse {}", receipt.getId(), receipt.getType(), warehouse.getId());
        return mapToResponse(receipt, savedItems);
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
        requireActiveWarehouseContract(approverTenantId, receipt.getWarehouse().getId());

        if (!subscriptionService.hasActiveSubscription(creatorTenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }

        List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receiptId);

        for (InventoryReceiptItem item : items) {
            UUID skuId = item.getSku().getId();
            UUID warehouseId = receipt.getWarehouse().getId();
            UUID rackId = item.getRack().getId();
            UUID binId = item.getBin().getId();

            if (receipt.getType() == DocumentType.INBOUND) {
                validateBinCapacity(item.getBin(), item.getQuantity());
                StockBatch batch = stockBatchRepository
                        .findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(skuId, warehouseId, rackId, binId)
                        .orElse(null);

                if (batch != null) {
                    batch.setQuantity(batch.getQuantity() + item.getQuantity());
                    stockBatchRepository.save(batch);
                } else {
                    batch = StockBatch.builder()
                            .skuId(skuId)
                            .warehouse(receipt.getWarehouse())
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
                        .findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(skuId, warehouseId, rackId, binId)
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
        requireActiveWarehouseContract(approverTenantId, receipt.getWarehouse().getId());

        if (!subscriptionService.hasActiveSubscription(creatorTenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }

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
        requireWarehouseAccess(user, tenantId, warehouseId);
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
        requireWarehouseAccess(user, tenantId, receipt.getWarehouse().getId());
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
                .rejectReason(receipt.getRejectReason())
                .items(itemResponses)
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


        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .createdBy(creator)
                .type(type)
                .signatureData(null)
                .status(ApprovalStatus.APPROVED)
                .referenceId(auditId)
                .build();
        receipt = receiptRepository.save(receipt);


        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        ProductSku sku = productSkuRepository.findByIdAndIsDeletedFalse(batch.getSkuId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .receipt(receipt)
                .sku(sku)
                .quantity(quantity)
                .rack(batch.getRack())
                .bin(batch.getBin())
                .note("Điều chỉnh tự động từ kiểm kê #" + auditId)
                .build();
        receiptItemRepository.save(item);


        int delta = (type == DocumentType.INBOUND) ? quantity : -quantity;
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




    @Transactional
    public void recordTransaction(UUID receiptId, UUID batchId, int qty) {
        InventoryReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));
        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_BATCH_NOT_FOUND));
        InventoryTransaction tx = InventoryTransaction.builder()
                .receipt(receipt)
                .batch(batch)
                .quantityChanged(qty)
                .build();
        transactionRepository.save(tx);
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
        csv.append("STT,Mã Phiếu,Loại Phiếu,Kho Bãi,Trạng Thái,Mã SKU,Tên Sản Phẩm,Đơn Vị Tính,Số Lượng,Người Tạo,Thời Gian Tạo\n");

        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int stt = 1;

        for (InventoryReceipt receipt : page.getContent()) {
            List<InventoryReceiptItem> items = receiptItemRepository.findByReceiptId(receipt.getId());
            String warehouseName = escapeCsvField(receipt.getWarehouse() != null ? receipt.getWarehouse().getName() : "");
            String typeStr = receipt.getType() == DocumentType.INBOUND ? "Nhập kho" : "Xuất kho";
            String statusStr = mapStatusToVietnamese(receipt.getStatus());
            String createdByStr = escapeCsvField(receipt.getCreatedBy() != null ? receipt.getCreatedBy().getFullName() : "");
            String formattedDate = receipt.getCreatedAt() != null ? receipt.getCreatedAt().format(dateFormatter) : "";

            if (items.isEmpty()) {
                csv.append(String.format("%d,%s,\"%s\",%s,%s,-,-,-,0,\"%s\",%s\n",
                        stt++, receipt.getId(), typeStr, warehouseName, statusStr, createdByStr, formattedDate));
            } else {
                for (InventoryReceiptItem item : items) {
                    ProductSku sku = item.getSku();
                    String skuCode = sku != null ? sku.getSkuCode() : "-";
                    String skuName = escapeCsvField(sku != null ? sku.getName() : "-");
                    String uomName = sku != null && sku.getUom() != null ? sku.getUom().getName() : "-";

                    csv.append(String.format("%d,%s,%s,\"%s\",%s,%s,\"%s\",%s,%d,\"%s\",%s\n",
                            stt++, receipt.getId(), typeStr, warehouseName, statusStr, skuCode, skuName, uomName, item.getQuantity(), createdByStr, formattedDate));
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
        requireWarehouseAccess(user, tenantId, warehouseId);
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
        requireWarehouseAccess(user, tenantId, batch.getWarehouse().getId());
        return getTransactionsByBatch(batchId, pageable);
    }

    private void validateBinCapacity(WarehouseBin bin, int incomingQuantity) {
        if (bin == null) return;
        int currentQtyInBin = stockBatchRepository.sumQuantityByBinId(bin.getId());
        java.math.BigDecimal totalQtyAfterInbound = java.math.BigDecimal.valueOf((long) currentQtyInBin + incomingQuantity);

        if (bin.getMaxWeight() != null && bin.getMaxWeight().compareTo(java.math.BigDecimal.ZERO) > 0) {
            if (totalQtyAfterInbound.compareTo(bin.getMaxWeight()) > 0) {
                throw new BadRequestException("Vượt quá sức chứa trọng lượng tối đa của ô " + bin.getName() +
                        " (Tối đa: " + bin.getMaxWeight() + ", Hiện tại + Nhập mới: " + totalQtyAfterInbound + ")");
            }
        }

        if (bin.getMaxVolume() != null && bin.getMaxVolume().compareTo(java.math.BigDecimal.ZERO) > 0) {
            if (totalQtyAfterInbound.compareTo(bin.getMaxVolume()) > 0) {
                throw new BadRequestException("Vượt quá sức chứa thể tích tối đa của ô " + bin.getName() +
                        " (Tối đa: " + bin.getMaxVolume() + ", Hiện tại + Nhập mới: " + totalQtyAfterInbound + ")");
            }
        }
    }

    private UUID resolveTenantId(User user) {
        if (isStaff(user)) {
            return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(user.getId())
                    .map(member -> member.getTenant().getId())
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
        }
        return user.getId();
    }

    private void requireWarehouseAccess(User user, UUID tenantId, UUID warehouseId) {
        requireActiveWarehouseContract(tenantId, warehouseId);

        if (isStaff(user)
                && !assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                user.getId(), tenantId, warehouseId, AssignmentStatus.ACTIVE)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireActiveWarehouseContract(UUID tenantId, UUID warehouseId) {
        if (!rentalContractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isStaff(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName()));
    }
}
