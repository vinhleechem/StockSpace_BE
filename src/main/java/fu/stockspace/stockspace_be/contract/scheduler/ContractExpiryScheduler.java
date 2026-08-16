package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    private final RentalContractRepository contractRepository;
    private final WarehouseService warehouseService;
    private final WarehouseLayoutService warehouseLayoutService;
    private final StockBatchRepository stockBatchRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;
    private final WalletService walletService;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void expireContracts() {
        log.info("Starting daily contract expiry check...");

        LocalDate today = LocalDate.now();
        sendExpiryReminders(today);

        int expiryDays = systemConfigService.getIntValue("contract_expiry_days", 7);
        LocalDateTime threshold = LocalDateTime.now().minusDays(expiryDays);
        List<RentalContract> unsignedContracts = contractRepository.findByStatusAndSubmittedAtBefore(
                ContractStatus.PENDING_TENANT_CONFIRM, threshold);

        log.info("Found {} expired contracts waiting for Tenant signature", unsignedContracts.size());
        for (RentalContract contract : unsignedContracts) {
            try {
                cancelUnsignedContract(contract, expiryDays);
            } catch (Exception e) {
                log.error("Error processing unsigned contract ID = {}", contract.getId(), e);
            }
        }

        List<RentalContract> expiredActiveContracts = contractRepository.findActiveContractsEndingBefore(today);
        log.info("Found {} active contracts past their end date", expiredActiveContracts.size());
        for (RentalContract contract : expiredActiveContracts) {
            try {
                completeExpiredContract(contract, today);
            } catch (Exception e) {
                log.error("Error processing expired active contract ID = {}", contract.getId(), e);
            }
        }

        log.info("Daily contract expiry check finished.");
    }

    private void sendExpiryReminders(LocalDate today) {
        List<RentalContract> contracts = contractRepository.findActiveContractsEndingBetween(
                today.plusDays(29), today.plusMonths(1));

        for (RentalContract contract : contracts) {
            try {
                User tenant = contract.getBooking().getTenant();
                User owner = contract.getBooking().getWarehouse().getOwner();
                String warehouseName = contract.getBooking().getWarehouse().getName();

                if (tenant != null) {
                    emailService.sendContractExpiryReminderEmail(
                            tenant.getEmail(), tenant.getFullName(), warehouseName, contract.getEndDate());
                    notificationService.push(
                            tenant.getId(),
                            "Warehouse contract expiry reminder",
                            "Your rental contract for " + warehouseName + " expires on " + contract.getEndDate() + ".",
                            "RENTAL");
                }
                if (owner != null) {
                    emailService.sendContractExpiryReminderEmail(
                            owner.getEmail(), owner.getFullName(), warehouseName, contract.getEndDate());
                    notificationService.push(
                            owner.getId(),
                            "Warehouse contract expiry reminder",
                            "The rental contract for " + warehouseName + " expires on " + contract.getEndDate() + ".",
                            "RENTAL");
                }

                contract.setExpiryReminderSent(true);
                contractRepository.save(contract);
            } catch (Exception e) {
                log.error("Failed to send expiry reminder for contract {}", contract.getId(), e);
            }
        }
    }

    private void cancelUnsignedContract(RentalContract contract, int expiryDays) {
        User owner = contract.getBooking().getWarehouse().getOwner();
        walletService.refundBalance(
                owner.getId(),
                contract.getBooking().getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Deposit forfeited because the tenant did not confirm the contract in time: "
                        + contract.getBooking().getWarehouse().getName(),
                contract.getBooking().getId(),
                null);

        User tenant = contract.getBooking().getTenant();
        contract.setStatus(ContractStatus.CANCELLED);
        contract.setCancelReason("Tenant did not confirm the online contract within " + expiryDays + " days");
        contractRepository.save(contract);
        warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());

        if (tenant != null) {
            notificationService.push(
                    tenant.getId(),
                    "Rental contract cancelled",
                    "Your rental contract for " + contract.getBooking().getWarehouse().getName()
                            + " was cancelled because it was not confirmed in time.",
                    "RENTAL");
        }
        if (owner != null) {
            notificationService.push(
                    owner.getId(),
                    "Rental contract cancelled",
                    "The tenant did not confirm the rental contract for "
                            + contract.getBooking().getWarehouse().getName()
                            + ". The deposit was transferred to your wallet.",
                    "RENTAL");
        }
    }

    private void completeExpiredContract(RentalContract contract, LocalDate today) {
        User tenant = contract.getBooking().getTenant();
        User owner = contract.getBooking().getWarehouse().getOwner();
        var warehouse = contract.getBooking().getWarehouse();

        List<StockBatch> activeBatches = stockBatchRepository
                .findAllByWarehouseIdAndIsDeletedFalse(warehouse.getId());
        activeBatches.forEach(batch -> {
            batch.setActive(false);
            batch.setDeleted(true);
        });
        if (!activeBatches.isEmpty()) {
            stockBatchRepository.saveAll(activeBatches);
        }

        if (tenant != null) {
            warehouseLayoutService.archiveTenantLayout(warehouse.getId(), tenant.getId());
        }

        if (tenant != null) {
            List<StaffWarehouseAssignment> assignments = assignmentRepository
                    .findByTenantIdAndWarehouseIdAndStatus(
                            tenant.getId(), warehouse.getId(), AssignmentStatus.ACTIVE);
            LocalDateTime now = LocalDateTime.now();
            assignments.forEach(assignment -> {
                assignment.setStatus(AssignmentStatus.REVOKED);
                assignment.setEndDate(now);
            });
            if (!assignments.isEmpty()) {
                assignmentRepository.saveAll(assignments);
            }
        }

        contract.setStatus(ContractStatus.COMPLETED);
        contract.setCancelReason("Rental contract expired on " + contract.getEndDate());
        contractRepository.save(contract);
        warehouseService.markAsAvailable(warehouse.getId());

        if (tenant != null) {
            notificationService.push(
                    tenant.getId(),
                    "Rental contract expired",
                    "The contract for " + warehouse.getName()
                            + " expired on " + contract.getEndDate()
                            + ". Stock batches and warehouse assignments were closed; product and history data were retained.",
                    "RENTAL");
        }
        if (owner != null) {
            notificationService.push(
                    owner.getId(),
                    "Rental contract expired",
                    "The contract for " + warehouse.getName()
                            + " expired on " + contract.getEndDate()
                            + ". The warehouse is available again and its owner layout can be updated.",
                    "RENTAL");
        }

        log.info("Expired contract {} completed on {}: stock cleared, tenant layout archived, staff assignments revoked",
                contract.getId(), today);
    }
}
