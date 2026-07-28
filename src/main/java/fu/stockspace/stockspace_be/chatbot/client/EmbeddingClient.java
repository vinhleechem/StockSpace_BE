package fu.stockspace.stockspace_be.chatbot.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Fault-tolerant OpenRouter embedding client.
 *
 * <p>The public batch API preserves input order and represents an unavailable
 * embedding with an empty list. Callers can therefore fall back to lexical
 * retrieval without making an external outage a chatbot outage.</p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    private static final int MAX_REMOTE_BATCH_SIZE = 64;

    private final WebClient webClient;
    private final String apiKey;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final Duration timeout;

    @Value("${app.openrouter.data-collection:deny}")
    private String dataCollection = "deny";

    @Value("${app.openrouter.zdr:true}")
    private boolean zeroDataRetention = true;

    @Value("${app.openrouter.embedding-max-concurrent-requests:8}")
    private int maxConcurrentRequests = 8;

    @Value("${app.openrouter.embedding-bulkhead-wait:100ms}")
    private Duration bulkheadWait = Duration.ofMillis(100);

    private volatile Semaphore requestSlots;

    public EmbeddingClient(
            WebClient webClient,
            @Value("${app.openrouter.api-key:}") String apiKey,
            @Value("${app.openrouter.embedding-model:openai/text-embedding-3-small}") String embeddingModel,
            @Value("${app.openrouter.embedding-dimensions:1536}") int embeddingDimensions,
            @Value("${app.openrouter.embedding-timeout-ms:3000}") long timeoutMillis
    ) {
        this.webClient = webClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
        this.embeddingDimensions = Math.max(1, embeddingDimensions);
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMillis));
    }

    public List<Float> getEmbedding(String text) {
        return getEmbedding(text, embeddingDimensions);
    }

    public List<Float> getEmbedding(String text, int dimensions) {
        List<List<Float>> embeddings = getEmbeddings(List.of(text == null ? "" : text), dimensions);
        return embeddings.isEmpty() ? List.of() : embeddings.get(0);
    }

    public List<List<Float>> getEmbeddings(List<String> texts) {
        return getEmbeddings(texts, embeddingDimensions);
    }

    /**
     * Embeds inputs in bounded remote batches while preserving input indexes.
     * Blank inputs and failed batches produce empty vectors at their positions.
     */
    public List<List<Float>> getEmbeddings(List<String> texts, int dimensions) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        int requestedDimensions = dimensions > 0 ? dimensions : embeddingDimensions;
        List<List<Float>> result = new ArrayList<>(
                Collections.nCopies(texts.size(), List.of())
        );
        List<Integer> nonBlankIndexes = new ArrayList<>();

        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text != null && !text.isBlank()) {
                nonBlankIndexes.add(index);
            }
        }

        for (int start = 0; start < nonBlankIndexes.size(); start += MAX_REMOTE_BATCH_SIZE) {
            int end = Math.min(start + MAX_REMOTE_BATCH_SIZE, nonBlankIndexes.size());
            List<Integer> indexes = nonBlankIndexes.subList(start, end);
            List<String> batch = indexes.stream()
                    .map(index -> texts.get(index).trim())
                    .toList();
            List<List<Float>> batchResult = requestBatchWithIsolation(
                    batch,
                    requestedDimensions
            );

            for (int offset = 0; offset < indexes.size(); offset++) {
                if (offset < batchResult.size()) {
                    result.set(indexes.get(offset), batchResult.get(offset));
                }
            }
        }

        return List.copyOf(result);
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    /**
     * Serializes a validated vector for the TEXT column. Empty input is null,
     * never the misleading literal {@code []}.
     */
    public String toVectorString(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return null;
        }

        StringBuilder serialized = new StringBuilder("[");
        for (int index = 0; index < vector.size(); index++) {
            Float value = vector.get(index);
            if (value == null || !Float.isFinite(value)) {
                return null;
            }
            if (index > 0) {
                serialized.append(',');
            }
            serialized.append(value);
        }
        return serialized.append(']').toString();
    }

    /**
     * Bisects only input-related 4xx failures. This isolates a permanently
     * invalid or oversized document without turning a provider outage,
     * authentication failure, or rate limit into a request storm.
     */
    private List<List<Float>> requestBatchWithIsolation(
            List<String> batch,
            int dimensions
    ) {
        BatchAttempt attempt = requestBatch(batch, dimensions);
        if (!attempt.inputSpecificFailure() || batch.size() <= 1) {
            return attempt.embeddings();
        }

        int midpoint = batch.size() / 2;
        List<List<Float>> isolated = new ArrayList<>(batch.size());
        isolated.addAll(requestBatchWithIsolation(
                batch.subList(0, midpoint),
                dimensions
        ));
        isolated.addAll(requestBatchWithIsolation(
                batch.subList(midpoint, batch.size()),
                dimensions
        ));
        return List.copyOf(isolated);
    }

    private BatchAttempt requestBatch(List<String> batch, int dimensions) {
        if (batch.isEmpty()) {
            return new BatchAttempt(List.of(), false);
        }
        if (apiKey.isBlank() || embeddingModel.isBlank()) {
            log.debug("[EmbeddingClient] Embedding disabled because credentials/model are not configured");
            return new BatchAttempt(emptyBatch(batch.size()), false);
        }

        Semaphore slots = getRequestSlots();
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(
                    Math.max(1, bulkheadWait.toMillis()),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                log.warn("[EmbeddingClient] Embedding bulkhead is full");
                return new BatchAttempt(emptyBatch(batch.size()), false);
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", batch);
            requestBody.put("dimensions", dimensions);
            requestBody.put("provider", Map.of(
                    "data_collection",
                    "allow".equalsIgnoreCase(dataCollection) ? "allow" : "deny",
                    "zdr",
                    zeroDataRetention
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://stockspace.com")
                    .header("X-Title", "StockSpace Embedding")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout)
                    .block();

            return new BatchAttempt(
                    parseBatchResponse(response, batch.size(), dimensions),
                    false
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new BatchAttempt(emptyBatch(batch.size()), false);
        } catch (WebClientResponseException exception) {
            // Do not log the response body: providers can echo submitted text.
            boolean inputSpecificFailure = isInputSpecificFailure(exception);
            log.warn(
                    "[EmbeddingClient] Embedding batch rejected " +
                            "(items={}, status={}, isolatingInputs={})",
                    batch.size(),
                    exception.getStatusCode().value(),
                    inputSpecificFailure
            );
            return new BatchAttempt(
                    emptyBatch(batch.size()),
                    inputSpecificFailure
            );
        } catch (Exception exception) {
            // Do not include request text or exception messages: upstream errors
            // can echo the submitted plaintext.
            log.warn(
                    "[EmbeddingClient] Embedding batch failed (items={}, cause={})",
                    batch.size(),
                    exception.getClass().getSimpleName()
            );
            return new BatchAttempt(emptyBatch(batch.size()), false);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private boolean isInputSpecificFailure(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 400 || status == 413 || status == 422;
    }

    private Semaphore getRequestSlots() {
        Semaphore current = requestSlots;
        if (current == null) {
            synchronized (this) {
                current = requestSlots;
                if (current == null) {
                    current = new Semaphore(
                            Math.max(1, maxConcurrentRequests),
                            true
                    );
                    requestSlots = current;
                }
            }
        }
        return current;
    }

    private List<List<Float>> parseBatchResponse(
            Map<String, Object> response,
            int expectedItems,
            int expectedDimensions
    ) {
        List<List<Float>> result = new ArrayList<>(
                Collections.nCopies(expectedItems, List.of())
        );
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.copyOf(result);
        }

        int sequentialIndex = 0;
        for (Object dataItem : data) {
            if (!(dataItem instanceof Map<?, ?> item)) {
                sequentialIndex++;
                continue;
            }

            int index = item.get("index") instanceof Number number
                    ? number.intValue()
                    : sequentialIndex;
            sequentialIndex++;
            if (index < 0 || index >= expectedItems || !(item.get("embedding") instanceof List<?> rawVector)) {
                continue;
            }

            List<Float> vector = toValidatedVector(rawVector, expectedDimensions);
            if (!vector.isEmpty()) {
                result.set(index, vector);
            }
        }
        return List.copyOf(result);
    }

    private List<Float> toValidatedVector(List<?> rawVector, int expectedDimensions) {
        if (rawVector.size() != expectedDimensions) {
            return List.of();
        }

        List<Float> vector = new ArrayList<>(rawVector.size());
        for (Object value : rawVector) {
            if (!(value instanceof Number number)) {
                return List.of();
            }
            float converted = number.floatValue();
            if (!Float.isFinite(converted)) {
                return List.of();
            }
            vector.add(converted);
        }
        return List.copyOf(vector);
    }

    private List<List<Float>> emptyBatch(int size) {
        return new ArrayList<>(Collections.nCopies(size, List.of()));
    }

    private record BatchAttempt(
            List<List<Float>> embeddings,
            boolean inputSpecificFailure
    ) {
    }
}
