package fu.stockspace.stockspace_be.staff.service;

import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.dto.StaffOperationResponse;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a read-only Staff operation list from existing WMS aggregates.
 * No task entity is persisted and no WMS mutation is duplicated here.
 */
@Service
@RequiredArgsConstructor
public class StaffOperationsService {

    private static final String RECEIPT = "RECEIPT";
    private static final String AUDIT = "AUDIT";
    private static final String TRANSFER = "TRANSFER";
    private static final String VIEW = "VIEW";
    private static final String SUBMIT = "SUBMIT";

    private final TenantMemberRepository tenantMemberRepository;
    private final TenantWarehouseAccessService accessService;
    private final InventoryReceiptRepository receiptRepository;
    private final InventoryAuditRepository auditRepository;
    private final StockTransferRepository transferRepository;

    @Transactional(readOnly = true)
    public PagedResponse<StaffOperationResponse> getOperations(
            UUID staffId,
            UUID tenantId,
            UUID warehouseId,
            String type,
            String status,
            Pageable pageable) {
        UUID resolvedTenantId = resolveTenantId(staffId, tenantId);
        List<Warehouse> accessibleWarehouses = accessService
                .findAccessibleContractWarehouses(resolvedTenantId, staffId);
        Set<UUID> accessibleWarehouseIds = accessibleWarehouses.stream()
                .map(Warehouse::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (warehouseId != null && !accessibleWarehouseIds.contains(warehouseId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        List<UUID> warehouseIds = warehouseId == null
                ? new ArrayList<>(accessibleWarehouseIds)
                : List.of(warehouseId);
        if (warehouseIds.isEmpty()) {
            return emptyPage(pageable);
        }

        String normalizedType = normalizeType(type);
        String normalizedStatus = normalizeStatus(status);
        List<StaffOperationResponse> operations = new ArrayList<>();

        if (normalizedType == null || RECEIPT.equals(normalizedType)) {
            List<InventoryReceipt> receipts = receiptRepository
                    .findActiveOperationsByWarehouseIds(warehouseIds);
            receipts.stream()
                    .filter(receipt -> matchesReceiptStatus(receipt, normalizedStatus))
                    .map(this::toReceiptOperation)
                    .forEach(operations::add);
        }
        if (normalizedType == null || AUDIT.equals(normalizedType)) {
            List<InventoryAudit> audits = auditRepository.findActiveOperationsByWarehouseIds(warehouseIds);
            audits.stream()
                    .filter(audit -> matchesAuditStatus(audit, normalizedStatus))
                    .map(this::toAuditOperation)
                    .forEach(operations::add);
        }
        if (normalizedType == null || TRANSFER.equals(normalizedType)) {
            List<StockTransfer> transfers = transferRepository.findActiveOperationsForStaff(
                    resolvedTenantId, warehouseIds);
            transfers.stream()
                    .filter(transfer -> matchesTransferStatus(transfer, normalizedStatus))
                    .map(this::toTransferOperation)
                    .forEach(operations::add);
        }

        operations.sort(operationComparator());
        return page(operations, pageable);
    }

    private UUID resolveTenantId(UUID staffId, UUID tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId)
                .map(member -> member.getTenant().getId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN));
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(RECEIPT, AUDIT, TRANSFER).contains(normalized)) {
            throw new BadRequestException("type must be RECEIPT, AUDIT, or TRANSFER");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean matchesReceiptStatus(InventoryReceipt receipt, String status) {
        String current = receipt.getStatus().name();
        return status == null ? receipt.getStatus() == ApprovalStatus.PENDING : current.equals(status);
    }

    private boolean matchesAuditStatus(InventoryAudit audit, String status) {
        String current = audit.getStatus().name();
        return status == null
                ? EnumSet.of(AuditStatus.PENDING, AuditStatus.SUBMITTED).contains(audit.getStatus())
                : current.equals(status);
    }

    private boolean matchesTransferStatus(StockTransfer transfer, String status) {
        String current = transfer.getStatus().name();
        return status == null
                ? EnumSet.of(StockTransferStatus.PENDING, StockTransferStatus.IN_TRANSIT)
                        .contains(transfer.getStatus())
                : current.equals(status);
    }

    private StaffOperationResponse toReceiptOperation(InventoryReceipt receipt) {
        Warehouse warehouse = receipt.getWarehouse();
        return StaffOperationResponse.builder()
                .operationType(RECEIPT)
                .operationId(receipt.getId())
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .status(receipt.getStatus().name())
                .createdAt(receipt.getCreatedAt())
                .allowedActions(List.of(VIEW))
                .build();
    }

    private StaffOperationResponse toAuditOperation(InventoryAudit audit) {
        Warehouse warehouse = audit.getWarehouse();
        List<String> actions = audit.getStatus() == AuditStatus.PENDING
                ? List.of(VIEW, SUBMIT)
                : List.of(VIEW);
        return StaffOperationResponse.builder()
                .operationType(AUDIT)
                .operationId(audit.getId())
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .status(audit.getStatus().name())
                .createdAt(audit.getCreatedAt())
                .allowedActions(actions)
                .build();
    }

    private StaffOperationResponse toTransferOperation(StockTransfer transfer) {
        Warehouse source = transfer.getSourceWarehouse();
        Warehouse destination = transfer.getDestinationWarehouse();
        return StaffOperationResponse.builder()
                .operationType(TRANSFER)
                .operationId(transfer.getId())
                .warehouseId(source.getId())
                .warehouseName(source.getName())
                .sourceWarehouseId(source.getId())
                .sourceWarehouseName(source.getName())
                .destinationWarehouseId(destination.getId())
                .destinationWarehouseName(destination.getName())
                .status(transfer.getStatus().name())
                .createdAt(transfer.getCreatedAt())
                .allowedActions(List.of(VIEW))
                .build();
    }

    private Comparator<StaffOperationResponse> operationComparator() {
        return Comparator
                .comparing(StaffOperationResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StaffOperationResponse::getOperationType)
                .thenComparing(StaffOperationResponse::getOperationId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private PagedResponse<StaffOperationResponse> page(
            List<StaffOperationResponse> operations, Pageable pageable) {
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = Math.min(pageNumber * pageSize, operations.size());
        int to = Math.min(from + pageSize, operations.size());
        List<StaffOperationResponse> content = operations.subList(from, to);
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) operations.size() / pageSize);
        return PagedResponse.<StaffOperationResponse>builder()
                .content(content)
                .page(pageNumber)
                .size(pageSize)
                .totalElements(operations.size())
                .totalPages(totalPages)
                .last(to >= operations.size())
                .build();
    }

    private PagedResponse<StaffOperationResponse> emptyPage(Pageable pageable) {
        return PagedResponse.<StaffOperationResponse>builder()
                .content(List.of())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();
    }
}
