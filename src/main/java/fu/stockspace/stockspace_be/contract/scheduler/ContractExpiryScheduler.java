package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class ContractExpiryScheduler {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int EXPIRY_REMINDER_WINDOW_DAYS = 30;

    private final RentalContractRepository contractRepository;
    private final ContractExpiryProcessor contractExpiryProcessor;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void expireContracts() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        log.info("Starting direct rental contract lifecycle check for {}", today);

        processCandidates(
                contractRepository.findActiveContractIdsEndingBetween(
                        today, today.plusDays(EXPIRY_REMINDER_WINDOW_DAYS)),
                today,
                contractExpiryProcessor::sendExpiryReminder,
                "expiry reminder");

        processCandidates(
                contractRepository.findScheduledContractIdsDueOnOrBefore(today),
                today,
                contractExpiryProcessor::activateScheduledContract,
                "scheduled contract activation");

        processCandidates(
                contractRepository.findActiveContractIdsEndingBefore(today),
                today,
                contractExpiryProcessor::expireActiveContract,
                "contract expiry");

        log.info("Direct rental contract lifecycle check finished for {}", today);
    }

    private void processCandidates(
            List<UUID> contractIds,
            LocalDate today,
            BiConsumer<UUID, LocalDate> processor,
            String operation) {
        for (UUID contractId : contractIds) {
            try {
                processor.accept(contractId, today);
            } catch (RuntimeException exception) {
                log.error("Failed {} for contract {}", operation, contractId, exception);
            }
        }
    }
}
