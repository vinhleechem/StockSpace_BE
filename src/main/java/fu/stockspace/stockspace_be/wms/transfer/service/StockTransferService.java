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
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
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
import fu.stockspace.stockspace_be.wms.transfer.dto.AssignStockTransferDestinationStaffRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.ReceiveStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDecisionRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferEventResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferPickRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferPickLineRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferReconcileRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferReturnLineRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferReturnRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferRetryRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferAttemptResponse;
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
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferReservationRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferEventRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferCommandRepository;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservationStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferEvent;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferCommand;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReceiptDisposition;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferPickLine;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttempt;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptType;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferPickLineRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferAttemptRepository;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditLockService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransferService {

    private static final String TRANSFER_NOTIFICATION_TYPE = "TRANSFER";
    private static final Set<StockTransferStatus> DESTINATION_STAFF_ASSIGNABLE_STATUSES = Set.of(
            StockTransferStatus.PENDING,
            StockTransferStatus.ALLOCATED,
            StockTransferStatus.PICKING,
            StockTransferStatus.READY_TO_DISPATCH,
            StockTransferStatus.RETRY_REQUESTED,
            StockTransferStatus.IN_TRANSIT,
            StockTransferStatus.OVERDUE,
            StockTransferStatus.ARRIVED_AT_DESTINATION,
            StockTransferStatus.RECEIVING,
            StockTransferStatus.PARTIALLY_RECEIVED);

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
    private final StaffWarehouseAssignmentRepository staffAssignmentRepository;
    private final TenantWarehouseAccessService accessService;
    private final PhysicalLoadCalculator physicalLoadCalculator;
    private final NotificationService notificationService;
    private final InventoryAuditLockService inventoryAuditLockService;
    private final StockTransferReservationRepository reservationRepository;
    private final StockTransferEventRepository eventRepository;
    private final StockTransferCommandRepository commandRepository;
    private final StockTransferPickLineRepository pickLineRepository;
    private final StockTransferAttemptRepository attemptRepository;

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
        User sourceStaff = request.getSourceStaffId() == null ? null
                : resolveSourceStaff(request.getSourceStaffId(), tenantId, sourceWarehouse.getId());
        User destinationStaff = request.getDestinationStaffId() == null ? null
                : resolveDestinationStaff(request.getDestinationStaffId(), tenantId, destinationWarehouse.getId());

        Set<UUID> skuIds = new HashSet<>();
        StockTransfer transfer = StockTransfer.builder()
                .transferNo("TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase())
                .tenant(tenantUser(tenantId))
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .activeDestinationWarehouse(destinationWarehouse)
                .sourceStaff(sourceStaff)
                .destinationStaff(destinationStaff)
                .createdBy(creator)
                .note(request.getNote())
                .expectedArrivalAt(request.getExpectedArrivalAt() == null
                        ? LocalDateTime.now().plusHours(48) : request.getExpectedArrivalAt())
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
        createInitialAttempt(savedTransfer, creator);
        recordEvent(savedTransfer, null, savedTransfer.getStatus(), "CREATE", creator, null, null);
        notifyTransferCreated(savedTransfer, creator, tenantId);
        notifyDestinationStaffAssigned(savedTransfer, destinationStaff);
        return mapToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse assignDestinationStaff(
            UUID userId,
            UUID transferId,
            AssignStockTransferDestinationStaffRequest request,
            String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        String command = "ASSIGN_DESTINATION_STAFF";
        String requestHash = requestHash(command, transferId, request);
        StockTransferResponse previous = findPreviousCommandResult(
                tenantId, transfer, command, idempotencyKey, requestHash);
        if (previous != null) {
            return previous;
        }
        if (request == null || request.getDestinationStaffId() == null) {
            throw new BadRequestException(ErrorCode.STAFF_NOT_FOUND);
        }
        if (!DESTINATION_STAFF_ASSIGNABLE_STATUSES.contains(transfer.getStatus())) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        User destinationStaff = resolveDestinationStaff(
                request.getDestinationStaffId(), tenantId, activeDestinationWarehouse(transfer).getId());
        User currentStaff = transfer.getDestinationStaff();
        if (currentStaff != null && currentStaff.getId().equals(destinationStaff.getId())) {
            saveCommand(tenantId, transfer, command, idempotencyKey, requestHash);
            return mapToResponse(transfer);
        }

        transfer.setDestinationStaff(destinationStaff);
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt != null && attempt.getDestinationWarehouse() != null
                && attempt.getDestinationWarehouse().getId()
                .equals(activeDestinationWarehouse(transfer).getId())) {
            attempt.setDestinationStaff(destinationStaff);
            attemptRepository.save(attempt);
        }

        StockTransfer saved = transferRepository.save(transfer);
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? null : request.getReason().trim();
        recordEvent(saved, saved.getStatus(), saved.getStatus(),
                currentStaff == null ? command : "REASSIGN_DESTINATION_STAFF",
                actor, reason, idempotencyKey);
        saveCommand(tenantId, saved, command, idempotencyKey, requestHash);
        notifyDestinationStaffAssigned(saved, destinationStaff);
        return mapToResponse(saved);
    }

    /**
     * Reserve the source quantities without changing on-hand stock. This is
     * the production path for clients that need a real approval/allocation
     * step before dispatch. The legacy approve-dispatch endpoint remains
     * available for existing clients.
     */
    @Transactional
    public StockTransferResponse allocateTransfer(UUID userId, UUID transferId) {
        return allocateTransfer(userId, transferId, null);
    }

    @Transactional
    public StockTransferResponse allocateTransfer(UUID userId, UUID transferId, String idempotencyKey) {
        User allocator = findUser(userId);
        UUID tenantId = resolveTenantId(allocator);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        StockTransferResponse previousResult = findPreviousCommandResult(
                tenantId, transfer, "ALLOCATE", idempotencyKey,
                requestHash("ALLOCATE", transferId));
        if (previousResult != null) {
            return previousResult;
        }
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        requireAllocationAccess(allocator, tenantId, transfer);
        if (inventoryAuditLockService != null) {
            inventoryAuditLockService.assertMovementAllowed(transfer.getSourceWarehouse().getId());
        }

        List<LockedSourceAllocation> lockedAllocations = lockAndValidateSourceAllocations(transfer);
        if (reservationRepository != null) {
            for (LockedSourceAllocation locked : lockedAllocations) {
                long reservedByOtherTransfers = reservationRepository
                        .sumQuantityByBatchAndStatusExcludingTransfer(
                                locked.batch().getId(), StockTransferReservationStatus.ACTIVE, transferId);
                if ((long) locked.batch().getQuantity() - reservedByOtherTransfers
                        < locked.allocation().getQuantity()) {
                    throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_RESERVATION_CONFLICT,
                            "Tồn khả dụng của stock batch không đủ sau khi trừ các reservation đang hoạt động");
                }
                reservationRepository.save(StockTransferReservation.builder()
                        .transferItem(locked.item())
                        .sourceStockBatch(locked.batch())
                        .quantity(locked.allocation().getQuantity())
                        .status(StockTransferReservationStatus.ACTIVE)
                        .reservedBy(allocator)
                        .reservedAt(LocalDateTime.now())
                        .build());
                locked.item().setReservedQuantity(
                        locked.item().getReservedQuantity() + locked.allocation().getQuantity());
            }
        } else {
            lockedAllocations.forEach(locked -> locked.item().setReservedQuantity(
                    locked.item().getReservedQuantity() + locked.allocation().getQuantity()));
        }

        StockTransferStatus previous = transfer.getStatus();
        transfer.setStatus(StockTransferStatus.ALLOCATED);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        recordEvent(savedTransfer, previous, StockTransferStatus.ALLOCATED, "ALLOCATE", allocator, null, idempotencyKey);
        saveCommand(tenantId, savedTransfer, "ALLOCATE", idempotencyKey,
                requestHash("ALLOCATE", transferId));
        return mapToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse pickTransfer(UUID userId, UUID transferId,
                                              StockTransferPickRequest request) {
        return pickTransfer(userId, transferId, request, null);
    }

    @Transactional
    public StockTransferResponse pickTransfer(UUID userId, UUID transferId,
                                              StockTransferPickRequest request,
                                              String idempotencyKey) {
        User picker = findUser(userId);
        UUID tenantId = resolveTenantId(picker);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        StockTransferResponse previousResult = findPreviousCommandResult(
                tenantId, transfer, "PICK", idempotencyKey,
                requestHash("PICK", transferId, request));
        if (previousResult != null) {
            return previousResult;
        }
        if (transfer.getStatus() != StockTransferStatus.ALLOCATED
                && transfer.getStatus() != StockTransferStatus.PICKING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        accessService.requireActiveContract(tenantId, transfer.getSourceWarehouse().getId());
        accessService.requireActiveContract(tenantId, activeDestinationWarehouse(transfer).getId());
        accessService.requireActiveSubscription(tenantId);
        if (isStaff(picker)) {
            accessService.requireActiveStaffAssignment(picker.getId(), tenantId,
                    transfer.getSourceWarehouse().getId());
            if (transfer.getSourceStaff() != null
                    && !picker.getId().equals(transfer.getSourceStaff().getId())) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN,
                        "Chỉ source staff được gán mới được pick transfer này");
            }
        }
        if (inventoryAuditLockService != null) {
            inventoryAuditLockService.assertMovementAllowed(transfer.getSourceWarehouse().getId());
        }

        List<LockedSourceAllocation> lockedAllocations = lockAndValidateSourceAllocations(transfer);
        Map<UUID, LockedSourceAllocation> byAllocationId = lockedAllocations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        locked -> locked.allocation().getId(), locked -> locked));
        Set<UUID> submitted = new HashSet<>();
        for (StockTransferPickLineRequest line : request.getLines()) {
            if (!submitted.add(line.getSourceAllocationId())) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Một source allocation chỉ được xuất hiện một lần trong pick command");
            }
            LockedSourceAllocation locked = byAllocationId.get(line.getSourceAllocationId());
            if (locked == null) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Source allocation không thuộc transfer");
            }
            long alreadyPicked = pickLineRepository == null ? 0L
                    : pickLineRepository.sumPickedByAllocation(line.getSourceAllocationId());
            if (alreadyPicked + line.getQuantity() > locked.allocation().getQuantity()) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Số lượng pick vượt source allocation");
            }
            if (pickLineRepository != null) {
                pickLineRepository.save(StockTransferPickLine.builder()
                        .transfer(transfer)
                        .item(locked.item())
                        .sourceAllocation(locked.allocation())
                        .sourceStockBatch(locked.batch())
                        .sourceRack(locked.batch().getRack())
                        .sourceBin(locked.batch().getBin())
                        .quantity(line.getQuantity())
                        .pickedBy(picker)
                        .pickedAt(LocalDateTime.now())
                        .build());
            }
            locked.item().setPickedQuantity(locked.item().getPickedQuantity() + line.getQuantity());
        }

        StockTransferStatus previous = transfer.getStatus();
        boolean fullyPicked = transfer.getItems().stream()
                .allMatch(item -> item.getPickedQuantity() >= item.getRequestedQuantity());
        transfer.setStatus(fullyPicked ? StockTransferStatus.READY_TO_DISPATCH : StockTransferStatus.PICKING);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        recordEvent(savedTransfer, previous, savedTransfer.getStatus(), "PICK", picker, null, idempotencyKey);
        saveCommand(tenantId, savedTransfer, "PICK", idempotencyKey,
                requestHash("PICK", transferId, request));
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
            requireStaffTransferAccess(user, tenantId, transfer);
        }
        return mapToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferEventResponse> getTransferTimeline(UUID userId, UUID transferId) {
        User user = findUser(userId);
        UUID tenantId = resolveTenantId(user);
        StockTransfer transfer = transferRepository.findByIdAndTenantIdAndIsDeletedFalse(transferId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        if (isStaff(user)) {
            requireStaffTransferAccess(user, tenantId, transfer);
        }
        if (eventRepository == null) {
            return List.of();
        }
        return eventRepository.findByTransferIdOrderByCreatedAtAscIdAsc(transferId).stream()
                .map(event -> StockTransferEventResponse.builder()
                        .id(event.getId())
                        .attemptId(event.getAttempt() == null ? null : event.getAttempt().getId())
                        .attemptSequenceNo(event.getAttempt() == null ? null : event.getAttempt().getSequenceNo())
                        .fromStatus(event.getFromStatus())
                        .toStatus(event.getToStatus())
                        .command(event.getCommand())
                        .actor(actor(event.getActor()))
                        .reason(event.getReason())
                        .idempotencyKey(event.getIdempotencyKey())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * SLA escalation hook used by the scheduler.  It never changes inventory;
     * it only makes a silent in-transit transfer visible for operator action.
     */
    @Transactional
    public int markOverdueTransfers() {
        if (transferRepository == null) return 0;
        int count = 0;
        for (StockTransfer candidate : transferRepository.findOverdueTransfers(LocalDateTime.now())) {
            StockTransfer transfer = transferRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (transfer == null || transfer.getExpectedArrivalAt() == null
                    || !transfer.getExpectedArrivalAt().isBefore(LocalDateTime.now())
                    || (transfer.getStatus() != StockTransferStatus.IN_TRANSIT
                    && transfer.getStatus() != StockTransferStatus.ARRIVED_AT_DESTINATION
                    && transfer.getStatus() != StockTransferStatus.PARTIALLY_RECEIVED)) {
                continue;
            }
            StockTransferStatus from = transfer.getStatus();
            transfer.setOverdueAt(LocalDateTime.now());
            transfer.setStatus(StockTransferStatus.OVERDUE);
            StockTransfer saved = transferRepository.save(transfer);
            User tenant = transfer.getTenant();
            if (tenant != null) {
                recordEvent(saved, from, saved.getStatus(), "SLA_OVERDUE", tenant,
                        "QuÃ¡ expectedArrivalAt mÃ  chÆ°a nháº­n Ä‘á»§ hÃ ng", null);
                notifyTransferCreator(saved, "Chuyá»ƒn kho quÃ¡ SLA",
                        transferRoute(saved) + " Ä‘Ã£ quÃ¡ SLA nháº­n hÃ ng. Cáº§n xÃ¡c minh, retry hoáº·c quay Ä‘áº§u.",
                        "overdue");
            }
            count++;
        }
        return count;
    }

    @Transactional
    public StockTransferResponse approveDispatch(UUID userId, UUID transferId) {
        return approveDispatch(userId, transferId, null);
    }

    @Transactional
    public StockTransferResponse approveDispatch(UUID userId, UUID transferId, String idempotencyKey) {
        User approver = findUser(userId);
        if (isStaff(approver) || !hasRole(approver, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        UUID tenantId = resolveTenantId(approver);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        StockTransferResponse previousResult = findPreviousCommandResult(
                tenantId, transfer, "APPROVE_DISPATCH", idempotencyKey,
                requestHash("APPROVE_DISPATCH", transferId));
        if (previousResult != null) {
            return previousResult;
        }
        if (transfer.getStatus() != StockTransferStatus.PENDING
                && transfer.getStatus() != StockTransferStatus.ALLOCATED
                && transfer.getStatus() != StockTransferStatus.READY_TO_DISPATCH) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        if (inventoryAuditLockService != null) {
            inventoryAuditLockService.assertMovementAllowed(transfer.getSourceWarehouse().getId());
        }
        List<LockedSourceAllocation> lockedAllocations = lockAndValidateSourceAllocations(transfer);
        if (transfer.getStatus() == StockTransferStatus.ALLOCATED) {
            assertReservationCoverage(transfer, lockedAllocations);
        }
        assertAvailableAfterReservations(transfer, lockedAllocations);

        InventoryReceipt outboundReceipt = receiptRepository.save(InventoryReceipt.builder()
                .tenant(tenantUser(tenantId))
                .warehouse(transfer.getSourceWarehouse())
                .createdBy(approver)
                .type(DocumentType.OUTBOUND)
                .receiverName(activeDestinationWarehouse(transfer).getName())
                .status(ApprovalStatus.APPROVED)
                .referenceId(transfer.getId())
                .build());

        for (LockedSourceAllocation locked : lockedAllocations) {
            StockTransferSourceAllocation allocation = locked.allocation();
            StockTransferItem item = locked.item();
            StockBatch batch = locked.batch();
            int dispatchQuantity = dispatchQuantity(transfer, allocation);
            if (dispatchQuantity <= 0) {
                throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Không có số lượng pick hợp lệ để dispatch");
            }
            batch.setQuantity(batch.getQuantity() - dispatchQuantity);
            stockBatchRepository.save(batch);
            if (transfer.getStatus() != StockTransferStatus.READY_TO_DISPATCH) {
                item.setPickedQuantity(item.getPickedQuantity() + dispatchQuantity);
            }
            item.setShippedQuantity(item.getShippedQuantity() + dispatchQuantity);

            InventoryReceiptItem receiptItem = receiptItemRepository.save(InventoryReceiptItem.builder()
                    .receipt(outboundReceipt)
                    .sku(item.getSku())
                    .quantity(dispatchQuantity)
                    .rack(allocation.getSourceRack())
                    .bin(allocation.getSourceBin())
                    .stockBatch(batch)
                    .build());
            transactionRepository.save(InventoryTransaction.builder()
                    .receipt(outboundReceipt)
                    .batch(batch)
                    .quantityChanged(-dispatchQuantity)
                    .build());
        }

        consumeReservations(transfer);

        StockTransferStatus previous = transfer.getStatus();
        transfer.setApprovedBy(approver);
        transfer.setApprovedAt(java.time.LocalDateTime.now());
        transfer.setOutboundReceipt(outboundReceipt);
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        markCurrentAttemptInTransit(savedTransfer, approver,
                savedTransfer.getItems().stream().mapToInt(StockTransferItem::getShippedQuantity).sum());
        recordEvent(savedTransfer, previous, StockTransferStatus.IN_TRANSIT,
                "APPROVE_DISPATCH", approver, null, idempotencyKey);
        saveCommand(tenantId, savedTransfer, "APPROVE_DISPATCH", idempotencyKey,
                requestHash("APPROVE_DISPATCH", transferId));
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
        return receiveTransfer(userId, transferId, request, null);
    }

    @Transactional
    public StockTransferResponse receiveTransfer(UUID userId, UUID transferId,
                                                 ReceiveStockTransferRequest request,
                                                 String idempotencyKey) {
        User receiver = findUser(userId);
        UUID tenantId = resolveTenantId(receiver);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        requireDestinationReceivingAccess(receiver, tenantId, transfer);
        StockTransferResponse previousResult = findPreviousCommandResult(
                tenantId, transfer, "RECEIVE", idempotencyKey,
                requestHash("RECEIVE", transferId, request));
        if (previousResult != null) {
            return previousResult;
        }
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT
                && transfer.getStatus() != StockTransferStatus.OVERDUE
                && transfer.getStatus() != StockTransferStatus.ARRIVED_AT_DESTINATION
                && transfer.getStatus() != StockTransferStatus.RECEIVING
                && transfer.getStatus() != StockTransferStatus.PARTIALLY_RECEIVED
                && transfer.getStatus() != StockTransferStatus.RECONCILING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        if (inventoryAuditLockService != null) {
            inventoryAuditLockService.assertMovementAllowed(activeDestinationWarehouse(transfer).getId());
        }
        StockTransferStatus receivingFrom = transfer.getStatus();
        if (receivingFrom == StockTransferStatus.IN_TRANSIT
                || receivingFrom == StockTransferStatus.OVERDUE) {
            markCurrentAttemptArrived(transfer, receiver);
            recordEvent(transfer, receivingFrom,
                    StockTransferStatus.ARRIVED_AT_DESTINATION, "ARRIVE", receiver, null, idempotencyKey);
        }
        if (receivingFrom != StockTransferStatus.RECEIVING) {
            recordEvent(transfer,
                    receivingFrom == StockTransferStatus.IN_TRANSIT
                            || receivingFrom == StockTransferStatus.OVERDUE
                            ? StockTransferStatus.ARRIVED_AT_DESTINATION : receivingFrom,
                    StockTransferStatus.RECEIVING, "START_RECEIVING", receiver, null, idempotencyKey);
        }
        WarehouseLayout destinationLayout = findActiveTenantLayout(
                activeDestinationWarehouse(transfer).getId(), tenantId);
        List<DestinationAllocationReference> references = validateDestinationAllocations(
                transfer, request, destinationLayout, request.isAllowPartial());
        Map<UUID, WarehouseRack> lockedRacks = lockDestinationRacks(references, destinationLayout);
        Map<UUID, WarehouseBin> lockedBins = lockDestinationBins(references, lockedRacks, destinationLayout);
        validateDestinationCapacity(tenantId, transfer, references, lockedRacks, lockedBins);

        InventoryReceipt inboundReceipt = receiptRepository.save(InventoryReceipt.builder()
                .tenant(tenantUser(tenantId))
                .warehouse(activeDestinationWarehouse(transfer))
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

            StockBatch batch = null;
            StockTransferReceiptDisposition disposition = allocationRequest.getDisposition() == null
                    ? StockTransferReceiptDisposition.GOOD : allocationRequest.getDisposition();
            if (disposition == StockTransferReceiptDisposition.GOOD) {
                batch = stockBatchRepository.save(StockBatch.builder()
                        .skuId(item.getSku().getId())
                        .warehouse(activeDestinationWarehouse(transfer))
                        .rack(rack)
                        .bin(bin)
                        .quantity(allocationRequest.getQuantity())
                        .arrivalDate(java.time.LocalDateTime.now())
                        .build());
            }

            StockTransferDestinationAllocation destinationAllocation = item.getDestinationAllocations().stream()
                    .filter(existing -> existing.getDestinationRack() != null
                            && existing.getDestinationBin() != null
                            && existing.getDestinationRack().getId().equals(rack.getId())
                            && existing.getDestinationBin().getId().equals(bin.getId())
                            && (existing.getDisposition() == null
                            ? StockTransferReceiptDisposition.GOOD : existing.getDisposition()) == disposition)
                    .findFirst()
                    .orElseGet(() -> {
                        StockTransferDestinationAllocation created = StockTransferDestinationAllocation.builder()
                                .item(item)
                                .destinationRack(rack)
                                .destinationBin(bin)
                                .quantity(0)
                                .disposition(disposition)
                                .build();
                        item.getDestinationAllocations().add(created);
                        return created;
                    });
            destinationAllocation.setQuantity(destinationAllocation.getQuantity() + allocationRequest.getQuantity());
            item.setReceivedQuantity(item.getReceivedQuantity() + allocationRequest.getQuantity());
            if (disposition == StockTransferReceiptDisposition.GOOD) {
                item.setReceivedGoodQuantity(item.getReceivedGoodQuantity() + allocationRequest.getQuantity());
            } else {
                item.setReceivedDamagedQuantity(item.getReceivedDamagedQuantity() + allocationRequest.getQuantity());
            }
            receiptItemRepository.save(InventoryReceiptItem.builder()
                    .receipt(inboundReceipt)
                    .sku(item.getSku())
                    .quantity(allocationRequest.getQuantity())
                    .rack(rack)
                    .bin(bin)
                    .stockBatch(batch)
                    .build());
            if (batch != null) {
                transactionRepository.save(InventoryTransaction.builder()
                        .receipt(inboundReceipt)
                        .batch(batch)
                        .quantityChanged(allocationRequest.getQuantity())
                        .build());
            }
        }

        StockTransferStatus previous = StockTransferStatus.RECEIVING;
        boolean allReceived = transfer.getItems().stream()
                .allMatch(item -> item.getReceivedQuantity() >= item.getRequestedQuantity());
        boolean hasDamaged = transfer.getItems().stream()
                .anyMatch(item -> item.getReceivedDamagedQuantity() > 0);
        transfer.setReceivedBy(receiver);
        transfer.setReceivedAt(java.time.LocalDateTime.now());
        if (transfer.getInboundReceipt() == null) {
            transfer.setInboundReceipt(inboundReceipt);
        }
        transfer.setStatus(allReceived
                ? (hasDamaged ? StockTransferStatus.RECONCILING : StockTransferStatus.COMPLETED)
                : StockTransferStatus.PARTIALLY_RECEIVED);
        StockTransfer savedTransfer = transferRepository.save(transfer);
        markCurrentAttemptReceived(savedTransfer, receiver,
                references.stream().mapToInt(reference -> reference.request().getQuantity()).sum(),
                allReceived);
        recordEvent(savedTransfer, previous, savedTransfer.getStatus(), "RECEIVE", receiver, null, idempotencyKey);
        saveCommand(tenantId, savedTransfer, "RECEIVE", idempotencyKey,
                requestHash("RECEIVE", transferId, request));
        notifyTransferCreator(
                savedTransfer,
                "Chuyển kho đã hoàn tất",
                transferRoute(savedTransfer)
                        + " đã được tiếp nhận thành công. Tồn kho tại kho đích đã được cập nhật.",
                "receive");
        return mapToResponse(savedTransfer);
    }

    /** Mark the truck as physically present before the receiving clerk starts counting. */
    @Transactional
    public StockTransferResponse arriveTransfer(UUID userId, UUID transferId, String idempotencyKey) {
        User actor = findUser(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        requireDestinationReceivingAccess(actor, tenantId, transfer);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "ARRIVE",
                idempotencyKey, requestHash("ARRIVE", transferId));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT
                && transfer.getStatus() != StockTransferStatus.OVERDUE) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        requireTenantMutationAccess(tenantId, transfer);
        markCurrentAttemptArrived(transfer, actor);
        StockTransferStatus from = transfer.getStatus();
        transfer.setStatus(StockTransferStatus.ARRIVED_AT_DESTINATION);
        StockTransfer saved = transferRepository.save(transfer);
        recordEvent(saved, from, saved.getStatus(), "ARRIVE", actor, null, idempotencyKey);
        saveCommand(tenantId, saved, "ARRIVE", idempotencyKey, requestHash("ARRIVE", transferId));
        return mapToResponse(saved);
    }

    /** A destination can refuse a shipment before any quantity is booked into stock. */
    @Transactional
    public StockTransferResponse rejectReceipt(UUID userId, UUID transferId,
                                               StockTransferDecisionRequest request,
                                               String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "REJECT_RECEIPT",
                idempotencyKey, requestHash("REJECT_RECEIPT", transferId, request));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.IN_TRANSIT
                && transfer.getStatus() != StockTransferStatus.OVERDUE
                && transfer.getStatus() != StockTransferStatus.ARRIVED_AT_DESTINATION
                && transfer.getStatus() != StockTransferStatus.RECEIVING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        if (transfer.getItems().stream().anyMatch(item -> item.getReceivedQuantity() > 0)) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "ÄÃ£ nháº­n má»™t pháº§n hÃ ng, hÃ£y dÃ¹ng close-short thay vÃ¬ tá»« chá»‘i toÃ n chuyá»n");
        }
        requireTenantMutationAccess(tenantId, transfer);
        String reason = normalizeDecisionReason(request);
        StockTransferStatus from = transfer.getStatus();
        transfer.setDecisionReason(reason);
        transfer.setStatus(StockTransferStatus.RECEIVE_REJECTED);
        StockTransfer saved = transferRepository.save(transfer);
        markCurrentAttemptRejected(saved, actor, reason);
        recordEvent(saved, from, saved.getStatus(), "REJECT_RECEIPT", actor, reason, idempotencyKey);
        saveCommand(tenantId, saved, "REJECT_RECEIPT", idempotencyKey,
                requestHash("REJECT_RECEIPT", transferId, request));
        notifyTransferCreator(saved, "Kho Ä‘Ã­ch tá»« chá»‘i nháº­n chuyá»ƒn kho",
                transferRoute(saved) + " bá»‹ tá»« chá»‘i. LÃ½ do: " + reason, "reject-receipt");
        return mapToResponse(saved);
    }

    /** Close an open receiving session when the destination accepts a short shipment. */
    @Transactional
    public StockTransferResponse closeShortReceipt(UUID userId, UUID transferId,
                                                   StockTransferDecisionRequest request,
                                                   String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "CLOSE_SHORT",
                idempotencyKey, requestHash("CLOSE_SHORT", transferId, request));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.PARTIALLY_RECEIVED) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        boolean hasReceived = transfer.getItems().stream().anyMatch(item -> item.getReceivedQuantity() > 0);
        boolean incomplete = transfer.getItems().stream()
                .anyMatch(item -> item.getReceivedQuantity() < item.getRequestedQuantity());
        if (!hasReceived || !incomplete) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "KhÃ´ng cÃ³ phÃ¡t sinh nháº­n thiáº¿u Ä‘á»ƒ Ä‘Ã³ng nháº­n");
        }
        requireTenantMutationAccess(tenantId, transfer);
        String reason = normalizeDecisionReason(request);
        StockTransferStatus from = transfer.getStatus();
        transfer.setDecisionReason(reason);
        transfer.setStatus(StockTransferStatus.SHORT_RECEIVED);
        StockTransfer saved = transferRepository.save(transfer);
        markCurrentAttemptReceived(saved, actor, 0, true);
        recordEvent(saved, from, saved.getStatus(), "CLOSE_SHORT", actor, reason, idempotencyKey);
        saveCommand(tenantId, saved, "CLOSE_SHORT", idempotencyKey,
                requestHash("CLOSE_SHORT", transferId, request));
        return mapToResponse(saved);
    }

    /** Request a new destination for the quantities that are still in the transport chain. */
    @Transactional
    public StockTransferResponse retryTransfer(UUID userId, UUID transferId,
                                               StockTransferRetryRequest request,
                                               String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "RETRY",
                idempotencyKey, requestHash("RETRY", transferId, request));
        if (previous != null) return previous;
        if (request == null || request.getDestinationWarehouseId() == null) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        if (transfer.getStatus() != StockTransferStatus.RECEIVE_REJECTED
                && transfer.getStatus() != StockTransferStatus.SHORT_RECEIVED) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        Warehouse newDestination = findActiveWarehouse(request.getDestinationWarehouseId());
        Warehouse retrySource = activeDestinationWarehouse(transfer);
        if (retrySource.getId().equals(newDestination.getId())) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_SOURCE_DESTINATION_SAME);
        }
        accessService.requireActiveContract(tenantId, retrySource.getId());
        accessService.requireActiveContract(tenantId, newDestination.getId());
        accessService.requireActiveSubscription(tenantId);
        User destinationStaff = request.getDestinationStaffId() == null ? null
                : resolveDestinationStaff(request.getDestinationStaffId(), tenantId, newDestination.getId());
        int outstanding = outstandingQuantity(transfer);
        if (outstanding <= 0) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "KhÃ´ng cÃ²n sá»‘ lÆ°á»£ng Ä‘ang chá» Ä‘á»ƒ retry");
        }
        String reason = normalizeRetryReason(request.getReason());
        StockTransferStatus from = transfer.getStatus();
        transfer.setActiveDestinationWarehouse(newDestination);
        transfer.setDestinationStaff(destinationStaff);
        transfer.setExpectedArrivalAt(request.getExpectedArrivalAt() == null
                ? LocalDateTime.now().plusHours(48) : request.getExpectedArrivalAt());
        transfer.setOverdueAt(null);
        transfer.setDecisionReason(reason);
        transfer.setStatus(StockTransferStatus.RETRY_REQUESTED);
        StockTransfer saved = transferRepository.save(transfer);
        createAttempt(saved, StockTransferAttemptType.FORWARD, retrySource, newDestination,
                outstanding, actor, reason, destinationStaff);
        recordEvent(saved, from, saved.getStatus(), "RETRY", actor, reason, idempotencyKey);
        saveCommand(tenantId, saved, "RETRY", idempotencyKey,
                requestHash("RETRY", transferId, request));
        notifyDestinationStaffAssigned(saved, destinationStaff);
        return mapToResponse(saved);
    }

    @Transactional
    public StockTransferResponse dispatchRetry(UUID userId, UUID transferId, String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "DISPATCH_RETRY",
                idempotencyKey, requestHash("DISPATCH_RETRY", transferId));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.RETRY_REQUESTED) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        requireTenantMutationAccess(tenantId, transfer);
        StockTransferStatus from = transfer.getStatus();
        markCurrentAttemptInTransit(transfer, actor, outstandingQuantity(transfer));
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        StockTransfer saved = transferRepository.save(transfer);
        recordEvent(saved, from, saved.getStatus(), "DISPATCH_RETRY", actor, null, idempotencyKey);
        saveCommand(tenantId, saved, "DISPATCH_RETRY", idempotencyKey,
                requestHash("DISPATCH_RETRY", transferId));
        return mapToResponse(saved);
    }

    /** Start a return-to-source leg. No stock is silently created at this point. */
    @Transactional
    public StockTransferResponse requestReturn(UUID userId, UUID transferId,
                                               StockTransferDecisionRequest request,
                                               String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "REQUEST_RETURN",
                idempotencyKey, requestHash("REQUEST_RETURN", transferId, request));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.RECEIVE_REJECTED
                && transfer.getStatus() != StockTransferStatus.SHORT_RECEIVED
                && transfer.getStatus() != StockTransferStatus.RECONCILING
                && transfer.getStatus() != StockTransferStatus.PARTIALLY_RETURNED) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        int returnable = returnableQuantity(transfer);
        if (returnable <= 0) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "KhÃ´ng cÃ²n sá»‘ lÆ°á»£ng cÃ³ thá»ƒ return");
        }
        requireTenantMutationAccess(tenantId, transfer);
        String reason = normalizeDecisionReason(request);
        Warehouse returnSource = transfer.getStatus() == StockTransferStatus.PARTIALLY_RETURNED
                ? currentReturnAttemptSource(transfer) : activeDestinationWarehouse(transfer);
        StockTransferStatus from = transfer.getStatus();
        transfer.setDecisionReason(reason);
        transfer.setDestinationStaff(null);
        transfer.setStatus(StockTransferStatus.RETURN_REQUESTED);
        StockTransfer saved = transferRepository.save(transfer);
        createAttempt(saved, StockTransferAttemptType.RETURN, returnSource,
                transfer.getSourceWarehouse(), returnable, actor, reason);
        recordEvent(saved, from, saved.getStatus(), "REQUEST_RETURN", actor, reason, idempotencyKey);
        saveCommand(tenantId, saved, "REQUEST_RETURN", idempotencyKey,
                requestHash("REQUEST_RETURN", transferId, request));
        return mapToResponse(saved);
    }

    @Transactional
    public StockTransferResponse dispatchReturn(UUID userId, UUID transferId, String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "DISPATCH_RETURN",
                idempotencyKey, requestHash("DISPATCH_RETURN", transferId));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.RETURN_REQUESTED) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        requireTenantMutationAccess(tenantId, transfer);
        StockTransferStatus from = transfer.getStatus();
        markCurrentAttemptInTransit(transfer, actor, returnableQuantity(transfer));
        transfer.setStatus(StockTransferStatus.RETURN_IN_TRANSIT);
        StockTransfer saved = transferRepository.save(transfer);
        recordEvent(saved, from, saved.getStatus(), "DISPATCH_RETURN", actor, null, idempotencyKey);
        saveCommand(tenantId, saved, "DISPATCH_RETURN", idempotencyKey,
                requestHash("DISPATCH_RETURN", transferId));
        return mapToResponse(saved);
    }

    @Transactional
    public StockTransferResponse receiveReturn(UUID userId, UUID transferId,
                                               StockTransferReturnRequest request,
                                               String idempotencyKey) {
        User actor = findTenantActor(userId);
        UUID tenantId = resolveTenantId(actor);
        StockTransfer transfer = lockTenantTransfer(tenantId, transferId);
        StockTransferResponse previous = findPreviousCommandResult(tenantId, transfer, "RECEIVE_RETURN",
                idempotencyKey, requestHash("RECEIVE_RETURN", transferId, request));
        if (previous != null) return previous;
        if (transfer.getStatus() != StockTransferStatus.RETURN_IN_TRANSIT) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        requireTenantMutationAccess(tenantId, transfer);
        if (inventoryAuditLockService != null) {
            inventoryAuditLockService.assertMovementAllowed(transfer.getSourceWarehouse().getId());
        }
        WarehouseLayout sourceLayout = findActiveTenantLayout(transfer.getSourceWarehouse().getId(), tenantId);
        Map<UUID, StockTransferItem> items = transfer.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(StockTransferItem::getId, item -> item));
        Set<UUID> seen = new HashSet<>();
        int incoming = 0;
        Map<UUID, WarehouseRack> returnRacks = new LinkedHashMap<>();
        Map<UUID, WarehouseBin> returnBins = new LinkedHashMap<>();
        for (StockTransferReturnLineRequest line : request.getLines()) {
            if (!seen.add(line.getItemId()) || !items.containsKey(line.getItemId())) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Return line khÃ´ng thuá»™c transfer hoáº·c bá»‹ láº·p");
            }
            StockTransferItem item = items.get(line.getItemId());
            int available = Math.max(0, item.getShippedQuantity()
                    - item.getReceivedGoodQuantity() - item.getReturnedQuantity());
            if (line.getQuantity() > available) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Sá»‘ lÆ°á»£ng return vÆ°á»£t pháº§n hÃ ng cÃ²n cÃ³ thá»ƒ thu há»“i");
            }
            WarehouseRack rack = findAndValidateReturnRack(line.getSourceRackId(), sourceLayout);
            WarehouseBin bin = findAndValidateReturnBin(line.getSourceBinId(), rack, sourceLayout);
            returnRacks.put(line.getSourceRackId(), rack);
            returnBins.put(line.getSourceBinId(), bin);
        }
        validateReturnCapacity(tenantId, transfer, request.getLines(), items, returnRacks, returnBins);
        InventoryReceipt receipt = receiptRepository.save(InventoryReceipt.builder()
                .tenant(tenantUser(tenantId))
                .warehouse(transfer.getSourceWarehouse())
                .createdBy(actor)
                .type(DocumentType.INBOUND)
                .senderName(activeDestinationWarehouse(transfer).getName())
                .status(ApprovalStatus.APPROVED)
                .referenceId(transfer.getId())
                .build());
        for (StockTransferReturnLineRequest line : request.getLines()) {
            StockTransferItem item = items.get(line.getItemId());
            WarehouseRack rack = returnRacks.get(line.getSourceRackId());
            WarehouseBin bin = returnBins.get(line.getSourceBinId());
            StockBatch batch = stockBatchRepository.save(StockBatch.builder()
                    .skuId(item.getSku().getId())
                    .warehouse(transfer.getSourceWarehouse())
                    .rack(rack)
                    .bin(bin)
                    .quantity(line.getQuantity())
                    .arrivalDate(LocalDateTime.now())
                    .build());
            receiptItemRepository.save(InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .sku(item.getSku())
                    .quantity(line.getQuantity())
                    .rack(rack)
                    .bin(bin)
                    .stockBatch(batch)
                    .build());
            transactionRepository.save(InventoryTransaction.builder()
                    .receipt(receipt)
                    .batch(batch)
                    .quantityChanged(line.getQuantity())
                    .build());
            item.setReturnedQuantity(item.getReturnedQuantity() + line.getQuantity());
            incoming += line.getQuantity();
        }
        int remaining = returnableQuantity(transfer);
        if (!request.isAllowPartial() && remaining > 0) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Return chÆ°a Ä‘á»§ sá»‘ lÆ°á»£ng; báº­t allowPartial náº¿u nháº­n nhiá»u Ä‘á»£t");
        }
        StockTransferStatus from = transfer.getStatus();
        if (remaining == 0) {
            transfer.setActiveDestinationWarehouse(transfer.getSourceWarehouse());
        }
        transfer.setInboundReceipt(receipt);
        transfer.setStatus(remaining == 0 ? StockTransferStatus.RETURNED
                : StockTransferStatus.PARTIALLY_RETURNED);
        StockTransfer saved = transferRepository.save(transfer);
        markCurrentAttemptReturned(saved, actor, incoming, remaining == 0);
        recordEvent(saved, from, saved.getStatus(), "RECEIVE_RETURN", actor, null, idempotencyKey);
        saveCommand(tenantId, saved, "RECEIVE_RETURN", idempotencyKey,
                requestHash("RECEIVE_RETURN", transferId, request));
        return mapToResponse(saved);
    }

    @Transactional
    public StockTransferResponse reconcileTransfer(UUID userId, UUID transferId,
                                                   StockTransferReconcileRequest request) {
        return reconcileTransfer(userId, transferId, request, null);
    }

    @Transactional
    public StockTransferResponse reconcileTransfer(UUID userId, UUID transferId,
                                                   StockTransferReconcileRequest request,
                                                   String idempotencyKey) {
        User reconciler = findUser(userId);
        if (isStaff(reconciler) || !hasRole(reconciler, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        UUID tenantId = resolveTenantId(reconciler);
        StockTransfer transfer = transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
        StockTransferResponse previousResult = findPreviousCommandResult(
                tenantId, transfer, "RECONCILE", idempotencyKey,
                requestHash("RECONCILE", transferId, request));
        if (previousResult != null) {
            return previousResult;
        }
        if (transfer.getStatus() != StockTransferStatus.RECONCILING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }
        if (request == null || request.getResolution() == null) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Resolution của discrepancy là bắt buộc");
        }
        requireTenantMutationAccess(tenantId, transfer);
        boolean allReceived = transfer.getItems().stream()
                .allMatch(item -> item.getReceivedQuantity() >= item.getRequestedQuantity());
        if (!allReceived) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Chưa nhận đủ số lượng vật lý để reconcile");
        }
        StockTransferStatus previous = transfer.getStatus();
        String reason = request.getReason() == null ? request.getResolution().name() : request.getReason().trim();
        transfer.setDecisionReason(reason.isBlank() ? request.getResolution().name() : reason);
        if (request.getResolution() == fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReconciliationResolution.RETURN_TO_SOURCE) {
            transfer.setDestinationStaff(null);
            transfer.setStatus(StockTransferStatus.RETURN_REQUESTED);
        } else if (request.getResolution() == fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReconciliationResolution.DECLARE_LOST) {
            transfer.setStatus(StockTransferStatus.LOST);
        } else {
            transfer.setStatus(StockTransferStatus.COMPLETED);
        }
        StockTransfer savedTransfer = transferRepository.save(transfer);
        if (savedTransfer.getStatus() == StockTransferStatus.RETURN_REQUESTED) {
            createAttempt(savedTransfer, StockTransferAttemptType.RETURN,
                    activeDestinationWarehouse(savedTransfer), savedTransfer.getSourceWarehouse(),
                    returnableQuantity(savedTransfer), reconciler, savedTransfer.getDecisionReason());
        }
        recordEvent(savedTransfer, previous, savedTransfer.getStatus(),
                "RECONCILE_" + request.getResolution().name(), reconciler,
                transfer.getDecisionReason(), idempotencyKey);
        saveCommand(tenantId, savedTransfer, "RECONCILE", idempotencyKey,
                requestHash("RECONCILE", transferId, request));
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
        boolean cancellable = decision == StockTransferStatus.CANCELLED
                && (transfer.getStatus() == StockTransferStatus.PENDING
                || transfer.getStatus() == StockTransferStatus.ALLOCATED
                || transfer.getStatus() == StockTransferStatus.PICKING
                || transfer.getStatus() == StockTransferStatus.READY_TO_DISPATCH);
        if (!cancellable && transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_STATUS);
        }

        requireTenantMutationAccess(tenantId, transfer);
        String reason = normalizeDecisionReason(request);
        StockTransferStatus previous = transfer.getStatus();
        releaseReservations(transfer);
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
        recordEvent(savedTransfer, previous, decision,
                decision == StockTransferStatus.REJECTED ? "REJECT" : "CANCEL",
                actor, reason, null);
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

    private void consumeReservations(StockTransfer transfer) {
        if (reservationRepository != null) {
            List<StockTransferReservation> reservations = reservationRepository
                    .findActiveForTransferForUpdate(transfer.getId());
            LocalDateTime now = LocalDateTime.now();
            for (StockTransferReservation reservation : reservations) {
                reservation.setStatus(StockTransferReservationStatus.CONSUMED);
                reservation.setConsumedAt(now);
                reservation.setReleasedAt(null);
                reservationRepository.save(reservation);
            }
        }
        transfer.getItems().forEach(item -> item.setReservedQuantity(0));
    }

    private int dispatchQuantity(StockTransfer transfer, StockTransferSourceAllocation allocation) {
        if (transfer.getStatus() != StockTransferStatus.READY_TO_DISPATCH
                || pickLineRepository == null) {
            return allocation.getQuantity();
        }
        long picked = pickLineRepository.sumPickedByAllocation(allocation.getId());
        if (picked > Integer.MAX_VALUE) {
            throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Số lượng pick vượt giới hạn số nguyên");
        }
        return (int) picked;
    }

    private void assertReservationCoverage(StockTransfer transfer,
                                           List<LockedSourceAllocation> allocations) {
        if (reservationRepository == null) {
            return;
        }
        Map<UUID, Long> reservedByBatch = reservationRepository
                .findActiveForTransferForUpdate(transfer.getId()).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        reservation -> reservation.getSourceStockBatch().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.summingLong(StockTransferReservation::getQuantity)));
        for (LockedSourceAllocation allocation : allocations) {
            long reserved = reservedByBatch.getOrDefault(allocation.batch().getId(), 0L);
            if (reserved < allocation.allocation().getQuantity()) {
                throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        "Reservation của transfer không còn đủ để dispatch");
            }
        }
    }

    /**
     * A legacy direct-dispatch request must still respect reservations created
     * by other transfers.  The row lock on each batch serializes this check
     * with allocation and other outbound movements.
     */
    private void assertAvailableAfterReservations(StockTransfer transfer,
                                                  List<LockedSourceAllocation> allocations) {
        if (reservationRepository == null) {
            return;
        }
        for (LockedSourceAllocation allocation : allocations) {
            long reservedByOtherTransfers = reservationRepository
                    .sumQuantityByBatchAndStatusExcludingTransfer(
                            allocation.batch().getId(), StockTransferReservationStatus.ACTIVE,
                            transfer.getId());
            int dispatchQuantity = dispatchQuantity(transfer, allocation.allocation());
            if ((long) allocation.batch().getQuantity() - reservedByOtherTransfers < dispatchQuantity) {
                throw new ResourceConflictException(ErrorCode.STOCK_TRANSFER_RESERVATION_CONFLICT,
                        "Tồn khả dụng của stock batch không đủ sau khi trừ reservation đang hoạt động");
            }
        }
    }

    private void releaseReservations(StockTransfer transfer) {
        if (reservationRepository != null) {
            List<StockTransferReservation> reservations = reservationRepository
                    .findActiveForTransferForUpdate(transfer.getId());
            LocalDateTime now = LocalDateTime.now();
            for (StockTransferReservation reservation : reservations) {
                reservation.setStatus(StockTransferReservationStatus.RELEASED);
                reservation.setReleasedAt(now);
                reservationRepository.save(reservation);
            }
        }
        transfer.getItems().forEach(item -> item.setReservedQuantity(0));
    }

    private void recordEvent(StockTransfer transfer, StockTransferStatus from,
                             StockTransferStatus to, String command, User actor,
                             String reason, String idempotencyKey) {
        if (eventRepository == null || transfer == null || actor == null) {
            return;
        }
        eventRepository.save(StockTransferEvent.builder()
                .transfer(transfer)
                .attempt(currentAttempt(transfer))
                .fromStatus(from)
                .toStatus(to)
                .command(command)
                .actor(actor)
                .reason(reason)
                .idempotencyKey(idempotencyKey)
                .build());
    }

    private void saveCommand(UUID tenantId, StockTransfer transfer, String command,
                             String idempotencyKey, String requestHash) {
        if (commandRepository == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        commandRepository.save(StockTransferCommand.builder()
                .tenant(transfer.getTenant())
                .transfer(transfer)
                .command(command)
                .idempotencyKey(idempotencyKey.trim())
                .requestHash(requestHash)
                .resultStatus(transfer.getStatus())
                .processedAt(LocalDateTime.now())
                .build());
    }

    private StockTransferResponse findPreviousCommandResult(UUID tenantId, StockTransfer transfer,
                                                             String command, String idempotencyKey,
                                                             String requestHash) {
        if (commandRepository == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return commandRepository.findByTenantIdAndCommandAndIdempotencyKey(
                        tenantId, command, idempotencyKey.trim())
                .map(previous -> {
                    if (!requestHash.equals(previous.getRequestHash())) {
                        throw new ResourceConflictException(
                                "Idempotency-Key đã được dùng cho payload khác");
                    }
                    return mapToResponse(transfer);
                })
                .orElse(null);
    }

    private String requestHash(String command, UUID transferId) {
        return sha256(command + ":" + transferId);
    }

    private String requestHash(String command, UUID transferId, ReceiveStockTransferRequest request) {
        StringBuilder canonical = new StringBuilder(command).append(':').append(transferId)
                .append(':').append(request != null && request.isAllowPartial());
        if (request != null && request.getDestinationAllocations() != null) {
            request.getDestinationAllocations().stream()
                    .sorted(Comparator.comparing(StockTransferDestinationAllocationRequest::getItemId)
                            .thenComparing(StockTransferDestinationAllocationRequest::getDestinationRackId)
                            .thenComparing(StockTransferDestinationAllocationRequest::getDestinationBinId))
                    .forEach(allocation -> canonical.append('|')
                            .append(allocation.getItemId()).append(':')
                            .append(allocation.getDestinationRackId()).append(':')
                            .append(allocation.getDestinationBinId()).append(':')
                            .append(allocation.getQuantity()).append(':')
                            .append(allocation.getDisposition()));
        }
        return sha256(canonical.toString());
    }

    private String requestHash(String command, UUID transferId, StockTransferPickRequest request) {
        StringBuilder canonical = new StringBuilder(command).append(':').append(transferId);
        if (request != null && request.getLines() != null) {
            request.getLines().stream()
                    .sorted(Comparator.comparing(StockTransferPickLineRequest::getSourceAllocationId))
                    .forEach(line -> canonical.append('|')
                            .append(line.getSourceAllocationId()).append(':').append(line.getQuantity()));
        }
        return sha256(canonical.toString());
    }

    private String requestHash(String command, UUID transferId,
                               StockTransferReconcileRequest request) {
        String canonical = command + ':' + transferId + ':'
                + (request == null || request.getResolution() == null
                ? "" : request.getResolution().name()) + ':'
                + (request == null || request.getReason() == null ? "" : request.getReason().trim());
        return sha256(canonical);
    }

    private String requestHash(String command, UUID transferId,
                               StockTransferDecisionRequest request) {
        return sha256(command + ':' + transferId + ':'
                + (request == null || request.getReason() == null ? "" : request.getReason().trim()));
    }

    private String requestHash(String command, UUID transferId,
                               StockTransferRetryRequest request) {
        StringBuilder canonical = new StringBuilder(command).append(':').append(transferId).append(':')
                .append(request == null ? "" : request.getDestinationWarehouseId());
        if (request != null && request.getDestinationStaffId() != null) {
            canonical.append(":staff=").append(request.getDestinationStaffId());
        }
        canonical.append(':').append(request == null ? "" : request.getExpectedArrivalAt()).append(':')
                .append(request == null || request.getReason() == null ? "" : request.getReason().trim());
        return sha256(canonical.toString());
    }

    private String requestHash(String command, UUID transferId,
                               AssignStockTransferDestinationStaffRequest request) {
        return sha256(command + ':' + transferId + ':'
                + (request == null ? "" : request.getDestinationStaffId()) + ':'
                + (request == null || request.getReason() == null ? "" : request.getReason().trim()));
    }

    private String requestHash(String command, UUID transferId,
                               StockTransferReturnRequest request) {
        StringBuilder canonical = new StringBuilder(command).append(':').append(transferId)
                .append(':').append(request != null && request.isAllowPartial());
        if (request != null && request.getLines() != null) {
            request.getLines().stream()
                    .sorted(Comparator.comparing(StockTransferReturnLineRequest::getItemId))
                    .forEach(line -> canonical.append('|').append(line.getItemId()).append(':')
                            .append(line.getQuantity()).append(':').append(line.getSourceRackId()).append(':')
                            .append(line.getSourceBinId()));
        }
        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
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

    private void notifyDestinationStaffAssigned(StockTransfer transfer, User destinationStaff) {
        if (destinationStaff == null || destinationStaff.getId() == null) {
            return;
        }
        notifySafely(
                destinationStaff.getId(),
                "Bạn được giao nhận chuyển kho",
                "Bạn được giao nhận " + transferRoute(transfer) + ".",
                "assign-destination-staff",
                transfer.getId());
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
                + "' đến kho '" + activeDestinationWarehouse(transfer).getName() + "'";
    }

    private User findTenantActor(UUID userId) {
        User actor = findUser(userId);
        if (isStaff(actor) || !hasRole(actor, RoleType.ROLE_TENANT)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private StockTransfer lockTenantTransfer(UUID tenantId, UUID transferId) {
        return transferRepository.findByIdForUpdate(transferId)
                .filter(candidate -> candidate.getTenant() != null
                        && tenantId.equals(candidate.getTenant().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_TRANSFER_NOT_FOUND));
    }

    private void createInitialAttempt(StockTransfer transfer, User actor) {
        if (attemptRepository == null) return;
        int planned = Math.max(1, transfer.getItems().stream()
                .mapToInt(StockTransferItem::getRequestedQuantity).sum());
        attemptRepository.save(StockTransferAttempt.builder()
                .transfer(transfer)
                .sequenceNo(1)
                .type(StockTransferAttemptType.OUTBOUND)
                .status(StockTransferAttemptStatus.PLANNED)
                .sourceWarehouse(transfer.getSourceWarehouse())
                .destinationWarehouse(activeDestinationWarehouse(transfer))
                .destinationStaff(transfer.getDestinationStaff())
                .plannedQuantity(planned)
                .createdBy(actor)
                .build());
    }

    private void createAttempt(StockTransfer transfer, StockTransferAttemptType type,
                               Warehouse source, Warehouse destination, int planned,
                               User actor, String reason) {
        createAttempt(transfer, type, source, destination, planned, actor, reason, null);
    }

    private void createAttempt(StockTransfer transfer, StockTransferAttemptType type,
                               Warehouse source, Warehouse destination, int planned,
                               User actor, String reason, User destinationStaff) {
        if (attemptRepository == null) return;
        int sequence = attemptRepository.findTopByTransferIdOrderBySequenceNoDesc(transfer.getId())
                .map(attempt -> attempt.getSequenceNo() + 1).orElse(1);
        attemptRepository.save(StockTransferAttempt.builder()
                .transfer(transfer)
                .sequenceNo(sequence)
                .type(type)
                .status(StockTransferAttemptStatus.PLANNED)
                .sourceWarehouse(source)
                .destinationWarehouse(destination)
                .destinationStaff(destinationStaff)
                .plannedQuantity(Math.max(1, planned))
                .reason(reason)
                .createdBy(actor)
                .build());
    }

    private StockTransferAttempt currentAttempt(StockTransfer transfer) {
        if (attemptRepository == null || transfer.getId() == null) return null;
        return attemptRepository.findTopByTransferIdOrderBySequenceNoDesc(transfer.getId()).orElse(null);
    }

    private Warehouse currentReturnAttemptSource(StockTransfer transfer) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        return attempt != null && attempt.getType() == StockTransferAttemptType.RETURN
                && attempt.getSourceWarehouse() != null
                ? attempt.getSourceWarehouse() : activeDestinationWarehouse(transfer);
    }

    private void markCurrentAttemptInTransit(StockTransfer transfer, User actor, int shippedQuantity) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt == null) return;
        LocalDateTime now = LocalDateTime.now();
        attempt.setStatus(StockTransferAttemptStatus.IN_TRANSIT);
        attempt.setStartedAt(now);
        attempt.setShippedQuantity(Math.min(attempt.getPlannedQuantity(), Math.max(0, shippedQuantity)));
        attempt.setCreatedBy(attempt.getCreatedBy() == null ? actor : attempt.getCreatedBy());
        attemptRepository.save(attempt);
    }

    private void markCurrentAttemptArrived(StockTransfer transfer, User actor) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt == null) return;
        attempt.setStatus(StockTransferAttemptStatus.ARRIVED);
        attempt.setArrivedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    private void markCurrentAttemptRejected(StockTransfer transfer, User actor, String reason) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt == null) return;
        attempt.setStatus(StockTransferAttemptStatus.REJECTED);
        attempt.setReason(reason);
        attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    private void markCurrentAttemptReceived(StockTransfer transfer, User actor,
                                             int receivedQuantity, boolean completed) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt == null) return;
        int received = Math.min(attempt.getPlannedQuantity(),
                attempt.getReceivedQuantity() + Math.max(0, receivedQuantity));
        attempt.setReceivedQuantity(received);
        attempt.setStatus(completed ? StockTransferAttemptStatus.RECEIVED
                : StockTransferAttemptStatus.PARTIALLY_RECEIVED);
        if (completed) attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    private void markCurrentAttemptReturned(StockTransfer transfer, User actor,
                                            int receivedQuantity, boolean completed) {
        StockTransferAttempt attempt = currentAttempt(transfer);
        if (attempt == null) return;
        attempt.setReceivedQuantity(Math.min(attempt.getPlannedQuantity(),
                attempt.getReceivedQuantity() + Math.max(0, receivedQuantity)));
        attempt.setStatus(completed ? StockTransferAttemptStatus.RETURNED
                : StockTransferAttemptStatus.PARTIALLY_RECEIVED);
        if (completed) attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
    }

    private int outstandingQuantity(StockTransfer transfer) {
        return transfer.getItems().stream()
                .mapToInt(item -> Math.max(0, item.getRequestedQuantity()
                        - item.getReceivedQuantity() - item.getReturnedQuantity()))
                .sum();
    }

    private int returnableQuantity(StockTransfer transfer) {
        return transfer.getItems().stream()
                .mapToInt(item -> Math.max(0, item.getShippedQuantity()
                        - item.getReceivedGoodQuantity() - item.getReturnedQuantity()))
                .sum();
    }

    private WarehouseRack findAndValidateReturnRack(UUID rackId, WarehouseLayout layout) {
        return rackRepository.findByIdForUpdate(rackId)
                .filter(rack -> rack.isActive() && rack.getLayout() != null
                        && layout.getId().equals(rack.getLayout().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RACK_NOT_FOUND));
    }

    private WarehouseBin findAndValidateReturnBin(UUID binId, WarehouseRack rack,
                                                  WarehouseLayout layout) {
        return binRepository.findByIdForUpdate(binId)
                .filter(bin -> bin.isActive() && bin.getRack() != null
                        && rack.getId().equals(bin.getRack().getId())
                        && bin.getRack().getLayout() != null
                        && layout.getId().equals(bin.getRack().getLayout().getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_BIN_NOT_FOUND));
    }

    private void validateReturnCapacity(UUID tenantId, StockTransfer transfer,
                                        List<StockTransferReturnLineRequest> lines,
                                        Map<UUID, StockTransferItem> items,
                                        Map<UUID, WarehouseRack> racks,
                                        Map<UUID, WarehouseBin> bins) {
        List<PhysicalLoadLine> current = stockBatchRepository
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(
                        transfer.getSourceWarehouse().getId(), tenantId);
        Map<UUID, List<PhysicalLoadLine>> byRack = current.stream()
                .filter(line -> line.rackId() != null)
                .collect(java.util.stream.Collectors.groupingBy(PhysicalLoadLine::rackId));
        Map<UUID, List<PhysicalLoadLine>> byBin = current.stream()
                .filter(line -> line.binId() != null)
                .collect(java.util.stream.Collectors.groupingBy(PhysicalLoadLine::binId));
        Map<UUID, List<PhysicalLoadLine>> incomingByRack = new LinkedHashMap<>();
        Map<UUID, List<PhysicalLoadLine>> incomingByBin = new LinkedHashMap<>();
        for (StockTransferReturnLineRequest line : lines) {
            ProductSku sku = items.get(line.getItemId()).getSku();
            PhysicalLoadLine loadLine = new PhysicalLoadLine(
                    line.getSourceRackId(), line.getSourceBinId(), sku.getId(), sku.getSkuCode(), sku.getName(),
                    sku.getUnitWeightKg(), sku.getUnitVolumeM3(), line.getQuantity());
            incomingByRack.computeIfAbsent(line.getSourceRackId(), ignored -> new ArrayList<>()).add(loadLine);
            incomingByBin.computeIfAbsent(line.getSourceBinId(), ignored -> new ArrayList<>()).add(loadLine);
        }
        for (UUID rackId : incomingByRack.keySet()) {
            WarehouseRack rack = racks.get(rackId);
            boolean weightLimited = physicalLoadCalculator.isLimited(rack.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(rack.getMaxVolume());
            if (!weightLimited && !volumeLimited) continue;
            List<PhysicalLoadLine> loads = new ArrayList<>(byRack.getOrDefault(rackId, List.of()));
            loads.addAll(incomingByRack.get(rackId));
            physicalLoadCalculator.assertWithinCapacity("rack", rack.getName(), rack.getMaxWeight(),
                    rack.getMaxVolume(), physicalLoadCalculator.calculate(loads, weightLimited, volumeLimited));
        }
        for (UUID binId : incomingByBin.keySet()) {
            WarehouseBin bin = bins.get(binId);
            boolean weightLimited = physicalLoadCalculator.isLimited(bin.getMaxWeight());
            boolean volumeLimited = physicalLoadCalculator.isLimited(bin.getMaxVolume());
            if (!weightLimited && !volumeLimited) continue;
            List<PhysicalLoadLine> loads = new ArrayList<>(byBin.getOrDefault(binId, List.of()));
            loads.addAll(incomingByBin.get(binId));
            physicalLoadCalculator.assertWithinCapacity("bin", bin.getName(), bin.getMaxWeight(),
                    bin.getMaxVolume(), physicalLoadCalculator.calculate(loads, weightLimited, volumeLimited));
        }
    }

    private String normalizeRetryReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_DECISION_REASON_REQUIRED);
        }
        return reason.trim();
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

    private void requireAllocationAccess(User allocator, UUID tenantId, StockTransfer transfer) {
        UUID sourceWarehouseId = transfer.getSourceWarehouse().getId();
        UUID destinationWarehouseId = activeDestinationWarehouse(transfer).getId();
        accessService.requireActiveContract(tenantId, sourceWarehouseId);
        accessService.requireActiveContract(tenantId, destinationWarehouseId);
        accessService.requireActiveSubscription(tenantId);
        if (!isStaff(allocator)) {
            return;
        }
        accessService.requireActiveStaffAssignment(allocator.getId(), tenantId, sourceWarehouseId);
        if (transfer.getSourceStaff() != null
                && allocator.getId().equals(transfer.getSourceStaff().getId())) {
            return;
        }
        accessService.requireActiveStaffAssignment(allocator.getId(), tenantId, destinationWarehouseId);
    }

    private void requireTenantMutationAccess(UUID tenantId, StockTransfer transfer) {
        accessService.requireActiveContract(tenantId, transfer.getSourceWarehouse().getId());
        accessService.requireActiveContract(tenantId, activeDestinationWarehouse(transfer).getId());
        accessService.requireActiveSubscription(tenantId);
    }

    private WarehouseLayout findActiveTenantLayout(UUID warehouseId, UUID tenantId) {
        return layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(layout -> layout.isActive() && !layout.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));
    }

    private List<DestinationAllocationReference> validateDestinationAllocations(
            StockTransfer transfer, ReceiveStockTransferRequest request, WarehouseLayout layout,
            boolean allowPartial) {
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

        if (references.isEmpty()) {
            throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                    "Phải có ít nhất một allocation nhận thực tế");
        }
        for (StockTransferItem item : transfer.getItems()) {
            long currentReceived = item.getReceivedQuantity();
            if (currentReceived == 0 && !item.getDestinationAllocations().isEmpty()) {
                currentReceived = item.getDestinationAllocations().stream()
                        .mapToLong(StockTransferDestinationAllocation::getQuantity).sum();
            }
            long incoming = quantitiesByItem.getOrDefault(item.getId(), 0L);
            if ((!allowPartial && !item.getDestinationAllocations().isEmpty())
                    || (!allowPartial && incoming != item.getRequestedQuantity())
                    || (allowPartial && currentReceived + incoming > item.getRequestedQuantity())) {
                throw new BadRequestException(ErrorCode.STOCK_TRANSFER_INVALID_ALLOCATION,
                        allowPartial
                                ? "Số lượng nhận lũy kế không được vượt requestedQuantity"
                                : "Tổng phân bổ đích phải bằng requestedQuantity của từng SKU");
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
                        activeDestinationWarehouse(transfer).getId(), tenantId);
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

    private void requireStaffTransferAccess(User staff, UUID tenantId, StockTransfer transfer) {
        User assignedSourceStaff = transfer.getSourceStaff();
        if (assignedSourceStaff != null && staff.getId().equals(assignedSourceStaff.getId())) {
            accessService.requireActiveStaffAssignment(staff.getId(), tenantId,
                    transfer.getSourceWarehouse().getId());
            return;
        }
        User assignedDestinationStaff = transfer.getDestinationStaff();
        if (assignedDestinationStaff != null
                && staff.getId().equals(assignedDestinationStaff.getId())) {
            accessService.requireActiveStaffAssignment(staff.getId(), tenantId,
                    activeDestinationWarehouse(transfer).getId());
            return;
        }
        requireStaffAssignments(staff.getId(), tenantId,
                transfer.getSourceWarehouse().getId(), activeDestinationWarehouse(transfer).getId());
    }

    private void requireDestinationReceivingAccess(User actor, UUID tenantId, StockTransfer transfer) {
        if (!isStaff(actor)) {
            if (!hasRole(actor, RoleType.ROLE_TENANT)) {
                throw new ForbiddenException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        User assignedDestinationStaff = transfer.getDestinationStaff();
        if (assignedDestinationStaff == null
                || !actor.getId().equals(assignedDestinationStaff.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Only the assigned destination staff can receive this transfer");
        }
        accessService.requireActiveStaffAssignment(
                actor.getId(), tenantId, activeDestinationWarehouse(transfer).getId());
    }

    private User resolveSourceStaff(UUID staffId, UUID tenantId, UUID sourceWarehouseId) {
        return resolveWarehouseStaff(staffId, tenantId, sourceWarehouseId, "sourceStaffId");
    }

    private User resolveDestinationStaff(UUID staffId, UUID tenantId, UUID destinationWarehouseId) {
        return resolveWarehouseStaff(staffId, tenantId, destinationWarehouseId, "destinationStaffId");
    }

    private User resolveWarehouseStaff(UUID staffId, UUID tenantId, UUID warehouseId, String fieldName) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND));
        if (!staff.isActive() || staff.isDeleted() || !isStaff(staff)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    fieldName + " phải là staff đang hoạt động");
        }
        if (!tenantMemberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(
                staffId, tenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Staff không thuộc tenant này");
        }
        if (staffAssignmentRepository == null
                || !staffAssignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN,
                    "Staff chưa được gán vào warehouse tương ứng");
        }
        return staff;
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
                .transferNo(transfer.getTransferNo())
                .status(transfer.getStatus())
                .sourceWarehouse(warehouseSummary(transfer.getSourceWarehouse()))
                .destinationWarehouse(warehouseSummary(transfer.getDestinationWarehouse()))
                .currentDestinationWarehouse(warehouseSummary(activeDestinationWarehouse(transfer)))
                .sourceStaff(actor(transfer.getSourceStaff()))
                .destinationStaff(actor(transfer.getDestinationStaff()))
                .note(transfer.getNote())
                .items(transfer.getItems().stream().map(this::mapItem).toList())
                .attempts(mapAttempts(transfer))
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
                .expectedArrivalAt(transfer.getExpectedArrivalAt())
                .overdueAt(transfer.getOverdueAt())
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
                .reservedQuantity(item.getReservedQuantity())
                .pickedQuantity(item.getPickedQuantity())
                .shippedQuantity(item.getShippedQuantity())
                .receivedQuantity(item.getReceivedQuantity())
                .receivedGoodQuantity(item.getReceivedGoodQuantity())
                .receivedDamagedQuantity(item.getReceivedDamagedQuantity())
                .returnedQuantity(item.getReturnedQuantity())
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
                .disposition(allocation.getDisposition() == null
                        ? StockTransferReceiptDisposition.GOOD : allocation.getDisposition())
                .build();
    }

    private List<StockTransferAttemptResponse> mapAttempts(StockTransfer transfer) {
        if (attemptRepository == null || transfer.getId() == null) {
            return List.of();
        }
        return attemptRepository.findByTransferIdOrderBySequenceNoAsc(transfer.getId()).stream()
                .map(attempt -> StockTransferAttemptResponse.builder()
                        .id(attempt.getId())
                        .sequenceNo(attempt.getSequenceNo())
                        .type(attempt.getType())
                        .status(attempt.getStatus())
                        .sourceWarehouse(warehouseSummary(attempt.getSourceWarehouse()))
                        .destinationWarehouse(warehouseSummary(attempt.getDestinationWarehouse()))
                        .destinationStaff(actor(attempt.getDestinationStaff()))
                        .plannedQuantity(attempt.getPlannedQuantity())
                        .shippedQuantity(attempt.getShippedQuantity())
                        .receivedQuantity(attempt.getReceivedQuantity())
                        .reason(attempt.getReason())
                        .createdBy(actor(attempt.getCreatedBy()))
                        .startedAt(attempt.getStartedAt())
                        .arrivedAt(attempt.getArrivedAt())
                        .completedAt(attempt.getCompletedAt())
                        .build())
                .toList();
    }

    private Warehouse activeDestinationWarehouse(StockTransfer transfer) {
        return transfer.getActiveDestinationWarehouse() == null
                ? transfer.getDestinationWarehouse() : transfer.getActiveDestinationWarehouse();
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
