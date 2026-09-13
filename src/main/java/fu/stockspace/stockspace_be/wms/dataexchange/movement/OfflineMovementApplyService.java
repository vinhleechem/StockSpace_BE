package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.inventory.StockFingerprintService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJob;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobService;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRow;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportRowRepository;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportType;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.dto.CreateInventoryReceiptRequest;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.ReceiptItemRequest;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a validated offline movement job as one transaction. Receipt and
 * stock mutation rules remain in InventoryReceiptService; this service only
 * translates grouped workbook rows into the existing receipt command.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMovementApplyService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WmsImportJobService jobService;
    private final WmsImportRowRepository rowRepository;
    private final InventoryReceiptService receiptService;
    private final StockFingerprintService fingerprintService;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final WarehouseRackRepository rackRepository;
    private final WarehouseBinRepository binRepository;
    private final ProductSkuRepository skuRepository;
    private final UserRepository userRepository;
    private final TenantWarehouseAccessService accessService;
    private final NotificationService notificationService;

    public OfflineMovementApplyResponse apply(UUID tenantId, UUID actorId, UUID jobId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (!isTenant(actor) || !tenantId.equals(actor.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        List<OfflineMovementApplyResponse.OfflineMovementReceiptResult> results =
                jobService.applyJob(tenantId, actorId, jobId, job -> applyLockedJob(
                        tenantId, actorId, actor, job));
        WmsImportJobResponse job = jobService.getJob(tenantId, actorId, jobId);
        return new OfflineMovementApplyResponse(job, List.copyOf(results));
    }

    private List<OfflineMovementApplyResponse.OfflineMovementReceiptResult> applyLockedJob(
            UUID tenantId, UUID actorId, User actor, WmsImportJob job) {
        if (job.getImportType() != WmsImportType.OFFLINE_MOVEMENT
                || job.getWarehouse() == null) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS,
                    "Import job không thuộc offline movement hợp lệ");
        }
        UUID warehouseId = job.getWarehouse().getId();

        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireWmsAccess(tenantId, warehouseId);

        String expectedFingerprint = metadataString(job, "stock_fingerprint");
        if (expectedFingerprint.isBlank()
                || !expectedFingerprint.equalsIgnoreCase(fingerprintService.fingerprint(warehouseId))) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }

        WarehouseLayout layout = currentTenantLayout(tenantId, warehouseId);
        List<WmsImportRow> rows = rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(job.getId());
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.getValidationErrors() != null
                && !row.getValidationErrors().isEmpty())) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS);
        }
        requireMovementPermissions(actor, rows);

        Map<String, List<WmsImportRow>> groups = new LinkedHashMap<>();
        for (WmsImportRow row : rows) {
            String movementRef = text(row.getGroupKey());
            if (movementRef.isBlank()) {
                throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
            }
            groups.computeIfAbsent(movementRef, ignored -> new ArrayList<>()).add(row);
        }
        List<Map.Entry<String, List<WmsImportRow>>> orderedGroups = new ArrayList<>(groups.entrySet());
        orderedGroups.sort(Comparator.comparingInt(entry -> sequence(entry.getValue().get(0))));

        List<OfflineMovementApplyResponse.OfflineMovementReceiptResult> results = new ArrayList<>();
        for (Map.Entry<String, List<WmsImportRow>> entry : orderedGroups) {
            List<WmsImportRow> groupRows = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(WmsImportRow::getRowNumber)).toList();
            InventoryReceiptResponse created = createAndApprove(
                    tenantId, actorId, warehouse, layout, entry.getKey(), groupRows);
            int sequenceNo = sequence(groupRows.get(0));
            for (WmsImportRow row : groupRows) {
                row.setResultResourceType("INVENTORY_RECEIPT");
                row.setResultResourceId(created.getId());
            }
            rowRepository.saveAll(groupRows);
            results.add(new OfflineMovementApplyResponse.OfflineMovementReceiptResult(
                    entry.getKey(), sequenceNo, created.getId()));
        }

        registerSummaryNotification(tenantId, warehouse.getName(), results.size());
        return results;
    }

    private void requireMovementPermissions(User actor, List<WmsImportRow> rows) {
        boolean inbound = rows.stream().anyMatch(row -> "INBOUND".equalsIgnoreCase(
                text(row.getNormalizedPayload().get("type"))));
        boolean outbound = rows.stream().anyMatch(row -> "OUTBOUND".equalsIgnoreCase(
                text(row.getNormalizedPayload().get("type"))));
        if ((inbound && !hasPermission(actor, "INBOUND_CREATE"))
                || (outbound && !hasPermission(actor, "OUTBOUND_CREATE"))) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean hasPermission(User user, String permission) {
        return user.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    private InventoryReceiptResponse createAndApprove(UUID tenantId, UUID actorId, Warehouse warehouse,
                                                      WarehouseLayout layout, String movementRef,
                                                      List<WmsImportRow> rows) {
        Map<String, Object> first = rows.get(0).getNormalizedPayload();
        DocumentType type = parseType(first.get("type"));
        LocalDateTime occurredAt = parseTimestamp(first.get("occurred_at"));
        if (occurredAt == null) throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);

        List<ReceiptItemRequest> items = rows.stream()
                .map(row -> toReceiptItem(tenantId, warehouse, layout, row))
                .toList();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouse.getId())
                .type(type)
                .senderName(text(first.get("sender_name")))
                .receiverName(text(first.get("receiver_name")))
                .items(items)
                .build();
        InventoryReceiptResponse pending = receiptService.createReceiptForOfflineImport(
                actorId, request, occurredAt);
        InventoryReceiptResponse approved = receiptService.approveReceiptForOfflineImport(
                actorId, pending.getId());
        if (approved.getStatus() != ApprovalStatus.APPROVED) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE,
                    "Receipt offline không chuyển được sang APPROVED: " + movementRef);
        }
        return approved;
    }

    private ReceiptItemRequest toReceiptItem(UUID tenantId, Warehouse warehouse,
                                             WarehouseLayout layout, WmsImportRow row) {
        Map<String, Object> payload = row.getNormalizedPayload();
        UUID skuId = uuid(payload, "sku_id");
        ProductSku sku = skuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId)
                .filter(ProductSku::isActive)
                .orElseThrow(() -> new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE));
        if (!sku.getSkuCode().equalsIgnoreCase(text(payload.get("sku_code")))) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
        int quantity = positiveInt(payload.get("quantity"));
        String rackCode = text(payload.get("rack_code"));
        String binCode = text(payload.get("bin_code"));
        UUID rackId = null;
        UUID binId = null;
        if (!rackCode.isBlank() || !binCode.isBlank()) {
            UUID layoutId = uuid(payload, "layout_id");
            rackId = uuid(payload, "rack_id");
            binId = uuid(payload, "bin_id");
            if (!layout.getId().equals(layoutId)) {
                throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
            }
            WarehouseRack rack = rackRepository.findByIdAndIsDeletedFalse(rackId)
                    .filter(item -> item.getLayout() != null && layoutId.equals(item.getLayout().getId()))
                    .filter(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(rackCode))
                    .orElseThrow(() -> new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE));
            WarehouseBin bin = binRepository.findByIdAndIsDeletedFalse(binId)
                    .filter(item -> item.getRack() != null && rack.getId().equals(item.getRack().getId()))
                    .filter(item -> item.getCode() != null && item.getCode().equalsIgnoreCase(binCode))
                    .orElseThrow(() -> new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE));
            if (rack.getLayout().getWarehouse() == null
                    || !warehouse.getId().equals(rack.getLayout().getWarehouse().getId())) {
                throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
            }
        }
        return ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(quantity)
                .rackId(rackId)
                .binId(binId)
                .note(text(payload.get("note")))
                .build();
    }

    private WarehouseLayout currentTenantLayout(UUID tenantId, UUID warehouseId) {
        return layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId)
                .filter(layout -> layout.isActive() && !layout.isDeleted())
                .orElseThrow(() -> new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE));
    }

    private void registerSummaryNotification(UUID tenantId, String warehouseName, int receiptCount) {
        Runnable send = () -> notificationService.push(
                tenantId,
                "Offline movement import completed",
                receiptCount + " receipt(s) were imported and approved for warehouse " + warehouseName + ".",
                "RECEIPT");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        send.run();
                    } catch (RuntimeException exception) {
                        log.warn("Failed to push offline import summary: {}", exception.getMessage());
                    }
                }
            });
        }
    }

    private boolean isTenant(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_TENANT.name().equals(role.getName()));
    }

    private String metadataString(WmsImportJob job, String key) {
        Object value = job.getContextMetadata() == null ? null : job.getContextMetadata().get(key);
        return text(value);
    }

    private DocumentType parseType(Object value) {
        try {
            return DocumentType.valueOf(text(value).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
    }

    private LocalDateTime parseTimestamp(Object value) {
        String text = text(value);
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(text);
                } catch (DateTimeParseException ignoredFinal) {
                    return null;
                }
            }
        }
    }

    private int sequence(WmsImportRow row) {
        return positiveInt(row.getNormalizedPayload().get("sequence_no"));
    }

    private int positiveInt(Object value) {
        try {
            int result = new java.math.BigDecimal(text(value)).intValueExact();
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
    }

    private UUID uuid(Map<String, Object> payload, String field) {
        try {
            return UUID.fromString(text(payload.get(field)));
        } catch (IllegalArgumentException exception) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_STALE);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
