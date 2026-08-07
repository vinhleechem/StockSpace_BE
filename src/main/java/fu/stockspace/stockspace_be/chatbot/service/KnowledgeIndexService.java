package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit, bounded indexer for the small in-application knowledge base.
 *
 * <p>Database work is deliberately split into two short transactions. The
 * embedding HTTP call runs between them and never holds a database connection
 * or entity transaction open.</p>
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexService {

    public static final int MAX_BATCH_SIZE = 64;

    private final SystemKnowledgeRepository knowledgeRepository;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NEVER)
    public ReindexResult reindexBatch(int requestedBatchSize) {
        if (embeddingClient.getEmbeddingDimensions() != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding provider dimensions must match pgvector schema: "
                            + SystemKnowledge.EMBEDDING_DIMENSIONS
            );
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        String model = embeddingClient.getEmbeddingModel();
        int dimensions = SystemKnowledge.EMBEDDING_DIMENSIONS;
        IndexSnapshot snapshot = transactionTemplate.execute(
                status -> loadSnapshot(model, dimensions, batchSize)
        );
        if (snapshot == null || snapshot.documents().isEmpty()) {
            int scanned = snapshot == null ? 0 : snapshot.scanned();
            int stale = snapshot == null ? 0 : snapshot.stale();
            boolean hasMore = snapshot != null && snapshot.hasMore();
            return new ReindexResult(scanned, stale, 0, 0, hasMore);
        }

        List<String> inputs = snapshot.documents().stream()
                .map(DocumentSnapshot::embeddingText)
                .toList();

        // External I/O is intentionally outside both transaction callbacks.
        List<List<Float>> embeddings = embeddingClient.getEmbeddings(inputs, dimensions);
        List<IndexedDocument> completed = new ArrayList<>();
        int failed = 0;
        for (int index = 0; index < snapshot.documents().size(); index++) {
            List<Float> vector = index < embeddings.size() ? embeddings.get(index) : List.of();
            float[] nativeVector = toValidatedNativeVector(vector);
            if (nativeVector == null) {
                failed++;
                continue;
            }
            completed.add(new IndexedDocument(
                    snapshot.documents().get(index),
                    nativeVector,
                    serializeVector(nativeVector),
                    model,
                    dimensions
            ));
        }

        int indexed = completed.isEmpty()
                ? 0
                : Objects.requireNonNullElse(
                        transactionTemplate.execute(status -> saveCompleted(completed)),
                        0
                );
        // A document edited/deactivated during the HTTP call is skipped during
        // save and counted as failed so it can be safely retried later.
        failed += completed.size() - indexed;

        return new ReindexResult(
                snapshot.scanned(),
                snapshot.stale(),
                indexed,
                failed,
                snapshot.hasMore()
        );
    }

    public boolean requiresReindex(SystemKnowledge document) {
        if (document == null
                || !isValidNativeVector(document.getEmbeddingVector())
                || document.getEmbeddingStr() == null
                || document.getEmbeddingStr().isBlank()
                || !Objects.equals(document.getEmbeddingModel(), embeddingClient.getEmbeddingModel())
                || !Objects.equals(
                        document.getEmbeddingDimensions(),
                        SystemKnowledge.EMBEDDING_DIMENSIONS
                )
                || !Objects.equals(document.getContentHash(), KnowledgeDocumentSupport.contentHash(document))) {
            return true;
        }

        return toValidatedNativeVector(
                KnowledgeDocumentSupport.parseVector(document.getEmbeddingStr())
        ) == null;
    }

    private IndexSnapshot loadSnapshot(String model, int dimensions, int batchSize) {
        List<SystemKnowledge> candidates = knowledgeRepository.findStaleIndexCandidates(
                model,
                dimensions,
                PageRequest.of(0, batchSize + 1)
        );
        boolean hasMore = candidates.size() > batchSize;
        List<SystemKnowledge> staleDocuments = candidates.stream()
                .limit(batchSize)
                .filter(this::requiresReindex)
                .toList();
        List<DocumentSnapshot> documents = staleDocuments.stream()
                .map(document -> new DocumentSnapshot(
                        document.getId(),
                        KnowledgeDocumentSupport.embeddingText(document),
                        KnowledgeDocumentSupport.contentHash(document)
                ))
                .toList();
        return new IndexSnapshot(
                candidates.size(),
                candidates.size(),
                documents,
                hasMore
        );
    }

    private int saveCompleted(List<IndexedDocument> completed) {
        List<UUID> ids = completed.stream()
                .map(indexed -> indexed.snapshot().id())
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, SystemKnowledge> currentDocuments = new HashMap<>();
        knowledgeRepository.findAllByIdInForUpdate(ids)
                .forEach(document -> currentDocuments.put(document.getId(), document));

        List<SystemKnowledge> safeToSave = new ArrayList<>();
        for (IndexedDocument indexed : completed) {
            SystemKnowledge current = currentDocuments.get(indexed.snapshot().id());
            if (current == null
                    || !current.isActive()
                    || current.isDeleted()
                    || !Objects.equals(
                            indexed.snapshot().contentHash(),
                            KnowledgeDocumentSupport.contentHash(current)
                    )
                    || !requiresReindex(current)) {
                continue;
            }

            current.setEmbeddingVector(Arrays.copyOf(
                    indexed.nativeVector(),
                    indexed.nativeVector().length
            ));
            current.setEmbeddingStr(indexed.serializedVector());
            current.setEmbeddingModel(indexed.model());
            current.setEmbeddingDimensions(indexed.dimensions());
            current.setContentHash(indexed.snapshot().contentHash());
            safeToSave.add(current);
        }

        if (!safeToSave.isEmpty()) {
            knowledgeRepository.saveAll(safeToSave);
        }
        return safeToSave.size();
    }

    private float[] toValidatedNativeVector(List<Float> vector) {
        if (vector == null || vector.size() != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return null;
        }

        float[] result = new float[SystemKnowledge.EMBEDDING_DIMENSIONS];
        double squaredNorm = 0.0;
        for (int index = 0; index < vector.size(); index++) {
            Float value = vector.get(index);
            if (value == null || !Float.isFinite(value)) {
                return null;
            }
            result[index] = value;
            squaredNorm += (double) value * value;
        }
        return squaredNorm > 0.0 && Double.isFinite(squaredNorm) ? result : null;
    }

    private boolean isValidNativeVector(float[] vector) {
        if (vector == null || vector.length != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return false;
        }

        double squaredNorm = 0.0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
            squaredNorm += (double) value * value;
        }
        return squaredNorm > 0.0 && Double.isFinite(squaredNorm);
    }

    private String serializeVector(float[] vector) {
        StringBuilder serialized = new StringBuilder(vector.length * 8);
        serialized.append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                serialized.append(',');
            }
            serialized.append(vector[index]);
        }
        return serialized.append(']').toString();
    }

    private record IndexSnapshot(
            int scanned,
            int stale,
            List<DocumentSnapshot> documents,
            boolean hasMore
    ) {
    }

    private record DocumentSnapshot(
            UUID id,
            String embeddingText,
            String contentHash
    ) {
    }

    private record IndexedDocument(
            DocumentSnapshot snapshot,
            float[] nativeVector,
            String serializedVector,
            String model,
            int dimensions
    ) {
    }

    public record ReindexResult(
            int scanned,
            int stale,
            int indexed,
            int failed,
            boolean hasMore
    ) {
    }
}
