package fu.stockspace.stockspace_be.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically refreshes warehouse embeddings after create/update/publication changes. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.chatbot.rag.warehouse-indexer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WarehouseSearchIndexScheduler {

    private final WarehouseSearchIndexService indexService;

    @Value("${app.chatbot.rag.warehouse-indexer.batch-size:32}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${app.chatbot.rag.warehouse-indexer.interval-ms:3600000}",
            initialDelayString = "${app.chatbot.rag.warehouse-indexer.initial-delay-ms:60000}"
    )
    public void reindexStaleWarehouses() {
        try {
            WarehouseSearchIndexService.ReindexResult result = indexService.reindexBatch(batchSize);
            if (result.stale() > 0) {
                log.info("[WarehouseSearchIndexScheduler] Search index batch completed "
                                + "(scanned={}, stale={}, indexed={}, failed={}, hasMore={})",
                        result.scanned(), result.stale(), result.indexed(), result.failed(), result.hasMore());
            }
        } catch (Exception exception) {
            log.warn("[WarehouseSearchIndexScheduler] Search index batch failed (cause={})",
                    exception.getClass().getSimpleName());
        }
    }
}
