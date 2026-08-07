package fu.stockspace.stockspace_be.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.chatbot.retention",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChatRetentionScheduler {

    private final ChatRetentionService retentionService;

    @Value("${app.chatbot.retention.guest-purge-after:7d}")
    private Duration guestPurgeAfter;

    @Value("${app.chatbot.retention.batch-size:200}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${app.chatbot.retention.interval-ms:3600000}",
            initialDelayString = "${app.chatbot.retention.initial-delay-ms:300000}"
    )
    public void purgeExpiredGuestChats() {
        try {
            int purged = retentionService.purgeExpiredGuestBatch(
                    guestPurgeAfter,
                    batchSize
            );
            if (purged > 0) {
                log.info("Purged {} expired guest chat session(s)", purged);
            }
        } catch (Exception exception) {
            log.warn("Guest chat retention batch failed (cause={})",
                    exception.getClass().getSimpleName());
        }
    }
}
