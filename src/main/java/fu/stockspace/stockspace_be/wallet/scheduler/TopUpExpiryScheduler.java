package fu.stockspace.stockspace_be.wallet.scheduler;

import fu.stockspace.stockspace_be.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cleans up abandoned VNPAY top-up attempts without touching wallet balance. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TopUpExpiryScheduler {

    private final WalletService walletService;

    @Scheduled(
            fixedDelayString = "${app.wallet.top-up.expiry-check-ms:60000}",
            initialDelayString = "${app.wallet.top-up.expiry-initial-delay-ms:60000}"
    )
    public void expirePendingTopUps() {
        try {
            int expired = walletService.expirePendingTopUps();
            if (expired > 0) {
                log.info("Marked {} abandoned VNPAY top-up(s) as EXPIRED", expired);
            }
        } catch (RuntimeException exception) {
            // A failed batch must not stop future scheduler runs.
            log.error("Failed to expire pending VNPAY top-ups", exception);
        }
    }
}
