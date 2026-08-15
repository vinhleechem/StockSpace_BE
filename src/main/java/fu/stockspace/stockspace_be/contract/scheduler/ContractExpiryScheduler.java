package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    private final RentalContractRepository contractRepository;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final WalletService walletService;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void expireContracts() {
        log.info("Starting daily contract expiry check...");

        int expiryDays = systemConfigService.getIntValue("contract_expiry_days", 7);
        LocalDateTime threshold = LocalDateTime.now().minusDays(expiryDays);

        List<RentalContract> expiredContracts = contractRepository.findByStatusAndSubmittedAtBefore(
                ContractStatus.PENDING_TENANT_CONFIRM, threshold);

        log.info("Found {} expired contracts waiting for Tenant signature", expiredContracts.size());

        for (RentalContract contract : expiredContracts) {
            try {
                log.info("Processing expired contract: ID = {}", contract.getId());


                User owner = contract.getBooking().getWarehouse().getOwner();
                walletService.refundBalance(
                        owner.getId(),
                        contract.getBooking().getDepositAmount(),
                        TransactionType.DEPOSIT_REFUND,
                        "Phạt cọc do Tenant quá hạn ký hợp đồng online: " + contract.getBooking().getWarehouse().getName(),
                        contract.getBooking().getId(),
                        null
                );
                log.info("Forfeited deposit of {} to Owner {}", contract.getBooking().getDepositAmount(), owner.getId());

                User tenant = contract.getBooking().getTenant();


                contract.setStatus(ContractStatus.CANCELLED);
                contract.setCancelReason("Tenant quá hạn xác nhận ký hợp đồng online (" + expiryDays + " ngày)");
                contractRepository.save(contract);


                warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());


                if (tenant != null) {
                    notificationService.push(
                            tenant.getId(),
                            "Hợp đồng thuê kho bị hủy tự động",
                            "Yêu cầu thuê kho '" + contract.getBooking().getWarehouse().getName() + "' đã bị hủy tự động do quá hạn ký hợp đồng (" + expiryDays + " ngày). Tiền cọc đã được khấu trừ sang cho Chủ kho.",
                            "RENTAL"
                    );
                }

                if (owner != null) {
                    notificationService.push(
                            owner.getId(),
                            "Hợp đồng thuê kho bị hủy tự động",
                            "Khách thuê đã quá hạn ký hợp đồng thuê kho '" + contract.getBooking().getWarehouse().getName() + "' (" + expiryDays + " ngày). Giao dịch đã bị hủy, tiền cọc đã được cộng vào ví của bạn và kho bãi hiện đã khả dụng trở lại.",
                            "RENTAL"
                    );
                }

                log.info("Contract {} processed successfully", contract.getId());
            } catch (Exception e) {
                log.error("Error processing expired contract ID = {}", contract.getId(), e);
            }
        }

        log.info("Daily contract expiry check finished.");
    }
}
