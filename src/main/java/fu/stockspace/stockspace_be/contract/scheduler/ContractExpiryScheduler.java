package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    private final RentalContractRepository contractRepository;
    private final WarehouseLayoutService warehouseLayoutService;
    private final StockBatchRepository stockBatchRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void expireContracts() {
        LocalDate today = LocalDate.now();
        log.info("Starting direct rental contract expiry check for {}", today);

        sendExpiryReminders(today);
        List<RentalContract> expiredContracts = contractRepository.findActiveContractsEndingBefore(today);
        for (RentalContract contract : expiredContracts) {
            try {
                expireActiveContract(contract, today);
            } catch (RuntimeException exception) {
                log.error("Failed to expire contract {}", contract.getId(), exception);
            }
        }

        log.info("Direct rental contract expiry check finished: {} candidate(s)", expiredContracts.size());
    }

    private void sendExpiryReminders(LocalDate today) {
        LocalDate reminderDate = today.plusDays(30);
        List<RentalContract> contracts = contractRepository.findActiveContractsEndingBetween(
                reminderDate, reminderDate);

        for (RentalContract contract : contracts) {
            if (contract.isExpiryReminderSent()) {
                continue;
            }
            User tenant = contract.getTenant();
            User owner = contract.getOwner();
            String warehouseName = contract.getWarehouse().getName();
            sendReminderBestEffort(tenant, warehouseName, contract.getEndDate(), true);
            sendReminderBestEffort(owner, warehouseName, contract.getEndDate(), false);

            contract.setExpiryReminderSent(true);
            contractRepository.save(contract);
        }
    }

    private void sendReminderBestEffort(
            User recipient, String warehouseName, LocalDate endDate, boolean tenant) {
        if (recipient == null) {
            return;
        }
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
        } catch (RuntimeException exception) {
            log.warn("Failed to push contract expiry reminder to user {}: {}",
                    recipient.getId(), exception.getMessage());
        }
    }

    private void expireActiveContract(RentalContract contract, LocalDate today) {
        if (contract.getStatus() != ContractStatus.ACTIVE
                || !contract.isActive()
                || contract.isDeleted()
                || contract.getEndDate() == null
                || !contract.getEndDate().isBefore(today)) {
            return;
        }
        User tenant = contract.getTenant();
        User owner = contract.getOwner();
        Warehouse warehouse = contract.getWarehouse();
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
        log.info("Expired direct contract {} on {}; cleanupSkipped={}",
                contract.getId(), today, hasActiveSibling);
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
        LocalDateTime now = LocalDateTime.now();
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
        if (recipient == null) {
            return;
        }
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
