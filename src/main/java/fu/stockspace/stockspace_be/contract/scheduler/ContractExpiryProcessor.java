package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractExpiryProcessor {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RentalContractRepository contractRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseLayoutService warehouseLayoutService;
    private final StockBatchRepository stockBatchRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public void sendExpiryReminder(UUID contractId, LocalDate today) {
        RentalContract contract = contractRepository.findByIdForUpdate(contractId).orElse(null);
        if (contract == null || contract.getStatus() != ContractStatus.ACTIVE
                || !contract.isActive() || contract.isDeleted()
                || contract.isExpiryReminderSent()) {
            return;
        }

        User tenant = contract.getTenant();
        User owner = contract.getOwner();
        Warehouse warehouse = contract.getWarehouse();
        if (tenant == null || owner == null || warehouse == null || contract.getEndDate() == null
                || contract.getEndDate().isBefore(today)
                || contract.getEndDate().isAfter(today.plusDays(30))) {
            return;
        }

        boolean tenantReminderSent = sendReminderBestEffort(
                tenant, warehouse.getName(), contract.getEndDate(), true);
        sendReminderBestEffort(owner, warehouse.getName(), contract.getEndDate(), false);

        if (tenantReminderSent) {
            contract.setExpiryReminderSent(true);
            contractRepository.save(contract);
        } else {
            log.warn("Tenant expiry reminder was not delivered for contract {}; will retry on the next run",
                    contract.getId());
        }
    }

    @Transactional
    public void activateScheduledContract(UUID contractId, LocalDate today) {
        RentalContract candidate = contractRepository.findById(contractId).orElse(null);
        if (candidate == null || candidate.getWarehouse() == null) {
            return;
        }

        Warehouse warehouse = warehouseRepository.findByIdForUpdate(candidate.getWarehouse().getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        RentalContract source = null;
        if (candidate.getRenewedFromContract() != null) {
            source = contractRepository.findByIdForUpdate(
                            candidate.getRenewedFromContract().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        }
        RentalContract successor = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));

        if (successor.getStatus() != ContractStatus.SCHEDULED
                || !successor.isActive() || successor.isDeleted()
                || successor.getStartDate() == null
                || successor.getStartDate().isAfter(today)) {
            return;
        }

        if (source == null) {
            successor.setStatus(ContractStatus.ACTIVE);
            contractRepository.save(successor);
            return;
        }

        if (!isValidRenewalHandover(source, successor, warehouse, today)) {
            throw new IllegalStateException(
                    "Scheduled renewal does not match its source contract: " + successor.getId());
        }

        // Promote the successor before expiring the source. Both changes are
        // committed atomically in this per-contract transaction.
        successor.setStatus(ContractStatus.ACTIVE);
        if (source.getStatus() == ContractStatus.ACTIVE) {
            source.setStatus(ContractStatus.EXPIRED);
        }
        contractRepository.save(successor);
        contractRepository.save(source);
        log.info("Activated renewal successor {} and expired source {} for warehouse {}",
                successor.getId(), source.getId(), warehouse.getId());
    }

    private boolean isValidRenewalHandover(RentalContract source,
                                           RentalContract successor,
                                           Warehouse warehouse,
                                           LocalDate today) {
        return source.getOwner() != null
                && source.getTenant() != null
                && source.getWarehouse() != null
                && successor.getOwner() != null
                && successor.getTenant() != null
                && successor.getWarehouse() != null
                && source.getOwner().getId().equals(successor.getOwner().getId())
                && source.getTenant().getId().equals(successor.getTenant().getId())
                && source.getWarehouse().getId().equals(successor.getWarehouse().getId())
                && warehouse.getId().equals(successor.getWarehouse().getId())
                && source.isActive()
                && !source.isDeleted()
                && (source.getStatus() == ContractStatus.ACTIVE
                || source.getStatus() == ContractStatus.EXPIRED)
                && source.getEndDate() != null
                && source.getStartDate() != null
                && successor.getStartDate().equals(source.getEndDate().plusDays(1))
                && !successor.getStartDate().isAfter(today);
    }

    @Transactional
    public void expireActiveContract(UUID contractId, LocalDate today) {
        RentalContract candidate = contractRepository.findById(contractId).orElse(null);
        if (candidate == null || candidate.getWarehouse() == null) {
            return;
        }
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(candidate.getWarehouse().getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        RentalContract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (contract.getStatus() != ContractStatus.ACTIVE
                || !contract.isActive()
                || contract.isDeleted()
                || contract.getEndDate() == null
                || !contract.getEndDate().isBefore(today)) {
            return;
        }

        User tenant = contract.getTenant();
        User owner = contract.getOwner();
        if (tenant == null || owner == null) {
            throw new IllegalStateException("Active contract relations are incomplete: " + contractId);
        }
        UUID tenantId = tenant.getId();
        UUID warehouseId = warehouse.getId();
        boolean hasActiveSibling = contractRepository.existsOtherCurrentDirectActiveContract(
                contract.getId(), tenantId, warehouseId, today);
        if (hasActiveSibling) {
            log.warn("Contract {} expired but shared Tenant-Warehouse data was retained because an active sibling exists",
                    contract.getId());
        } else {
            clearTenantOperationalStock(tenantId, warehouseId);
            warehouseLayoutService.archiveTenantLayout(warehouseId, tenantId);
            revokeAssignments(tenantId, warehouseId);
        }

        contract.setStatus(ContractStatus.EXPIRED);
        contractRepository.save(contract);
        notifyExpiryBestEffort(tenant, warehouse, true, hasActiveSibling);
        notifyExpiryBestEffort(owner, warehouse, false, hasActiveSibling);
        log.info("Expired direct rental contract {} on {}; cleanupSkipped={}",
                contract.getId(), today, hasActiveSibling);
    }

    private boolean sendReminderBestEffort(
            User recipient, String warehouseName, LocalDate endDate, boolean tenant) {
        try {
            emailService.sendContractExpiryReminderEmail(
                    recipient.getEmail(), recipient.getFullName(), warehouseName, endDate);
        } catch (RuntimeException exception) {
            log.warn("Failed to email contract expiry reminder to user {}: {}",
                    recipient.getId(), exception.getMessage());
        }
        try {
            notificationService.push(
                    recipient.getId(),
                    "Warehouse contract expiry reminder",
                    (tenant ? "Your" : "The") + " rental contract for " + warehouseName
                            + " expires on " + endDate + ".",
                    "CONTRACT_EXPIRY_REMINDER");
            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to push contract expiry reminder to user {}: {}",
                    recipient.getId(), exception.getMessage());
            return false;
        }
    }

    private void clearTenantOperationalStock(UUID tenantId, UUID warehouseId) {
        List<StockBatch> activeBatches = stockBatchRepository
                .findAllByWarehouseIdAndTenantId(warehouseId, tenantId);
        activeBatches.forEach(batch -> {
            batch.setActive(false);
            batch.setDeleted(true);
        });
        if (!activeBatches.isEmpty()) {
            stockBatchRepository.saveAll(activeBatches);
        }
    }

    private void revokeAssignments(UUID tenantId, UUID warehouseId) {
        List<StaffWarehouseAssignment> assignments = assignmentRepository
                .findByTenantIdAndWarehouseIdAndStatus(tenantId, warehouseId, AssignmentStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        assignments.forEach(assignment -> {
            assignment.setStatus(AssignmentStatus.REVOKED);
            assignment.setActive(false);
            assignment.setEndDate(now);
        });
        if (!assignments.isEmpty()) {
            assignmentRepository.saveAll(assignments);
        }
    }

    private void notifyExpiryBestEffort(
            User recipient, Warehouse warehouse, boolean tenant, boolean cleanupSkipped) {
        try {
            String suffix = cleanupSkipped
                    ? " Shared operational data was retained because another active contract exists."
                    : " Tenant operational access has been closed; historical records were retained.";
            notificationService.push(
                    recipient.getId(),
                    "Rental contract expired",
                    (tenant ? "Your contract" : "The contract") + " for " + warehouse.getName()
                            + " has expired." + suffix,
                    "CONTRACT_EXPIRED");
        } catch (RuntimeException exception) {
            log.warn("Failed to push contract expiry notification to user {}: {}",
                    recipient.getId(), exception.getMessage());
        }
    }
}
