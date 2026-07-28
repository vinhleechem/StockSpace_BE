package fu.stockspace.stockspace_be.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional maintenance trigger for bounded RAG indexing.
 *
 * <p>Disabled by default, so normal startup never calls the embedding
 * provider. Deployments that opt in should provide an embedding API key and
 * explicitly enable {@code app.chatbot.rag.indexer.enabled}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.chatbot.rag.indexer",
        name = "enabled",
        havingValue = "true"
)
public class KnowledgeIndexScheduler {

    private final KnowledgeIndexService knowledgeIndexService;

    @Value("${app.chatbot.rag.indexer.batch-size:32}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${app.chatbot.rag.indexer.interval-ms:3600000}",
            initialDelayString = "${app.chatbot.rag.indexer.initial-delay-ms:60000}"
    )
    public void reindexStaleKnowledge() {
        try {
            KnowledgeIndexService.ReindexResult result = knowledgeIndexService.reindexBatch(batchSize);
            if (result.stale() > 0) {
                log.info(
                        "[KnowledgeIndexScheduler] RAG index batch completed " +
                                "(scanned={}, stale={}, indexed={}, failed={}, hasMore={})",
                        result.scanned(),
                        result.stale(),
                        result.indexed(),
                        result.failed(),
                        result.hasMore()
                );
            }
        } catch (Exception exception) {
            log.warn(
                    "[KnowledgeIndexScheduler] RAG index batch failed (cause={})",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
