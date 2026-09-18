package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
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

/** Maintains embeddings for the public warehouse profile search index. */
@Service
@RequiredArgsConstructor
public class WarehouseSearchIndexService {

    public static final int MAX_BATCH_SIZE = 64;

    private final WarehouseRepository warehouseRepository;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NEVER)
    public ReindexResult reindexBatch(int requestedBatchSize) {
        if (embeddingClient.getEmbeddingDimensions() != Warehouse.SEARCH_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding provider dimensions must match warehouse search schema: "
                            + Warehouse.SEARCH_EMBEDDING_DIMENSIONS
            );
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        String model = embeddingClient.getEmbeddingModel();
        IndexSnapshot snapshot = transactionTemplate.execute(status -> loadSnapshot(model, batchSize));
        if (snapshot == null || snapshot.documents().isEmpty()) {
            int scanned = snapshot == null ? 0 : snapshot.scanned();
            int stale = snapshot == null ? 0 : snapshot.stale();
            return new ReindexResult(scanned, stale, 0, 0,
                    snapshot != null && snapshot.hasMore());
        }

        List<List<Float>> embeddings = embeddingClient.getEmbeddings(
                snapshot.documents().stream().map(DocumentSnapshot::embeddingText).toList(),
                Warehouse.SEARCH_EMBEDDING_DIMENSIONS
        );
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
                    model
            ));
        }

        int indexed = completed.isEmpty()
                ? 0
                : Objects.requireNonNullElse(
                        transactionTemplate.execute(status -> saveCompleted(completed)), 0);
        failed += completed.size() - indexed;
        return new ReindexResult(snapshot.scanned(), snapshot.stale(), indexed, failed,
                snapshot.hasMore());
    }

    public boolean requiresReindex(Warehouse warehouse) {
        return warehouse == null
                || !isValidNativeVector(warehouse.getSearchEmbedding())
                || warehouse.getSearchEmbeddingStr() == null
                || warehouse.getSearchEmbeddingStr().isBlank()
                || !Objects.equals(warehouse.getSearchEmbeddingModel(), embeddingClient.getEmbeddingModel())
                || !Objects.equals(warehouse.getSearchEmbeddingDimensions(),
                Warehouse.SEARCH_EMBEDDING_DIMENSIONS)
                || !Objects.equals(warehouse.getSearchContentHash(),
                WarehouseSearchDocumentSupport.contentHash(warehouse));
    }

    private IndexSnapshot loadSnapshot(String model, int batchSize) {
        List<Warehouse> candidates = warehouseRepository.findSearchIndexCandidates(
                PageRequest.of(0, batchSize + 1));
        boolean hasMore = candidates.size() > batchSize;
        List<Warehouse> stale = candidates.stream()
                .limit(batchSize)
                .filter(this::requiresReindex)
                .toList();
        List<DocumentSnapshot> documents = stale.stream()
                .map(warehouse -> new DocumentSnapshot(
                        warehouse.getId(),
                        WarehouseSearchDocumentSupport.embeddingText(warehouse),
                        WarehouseSearchDocumentSupport.contentHash(warehouse)
                ))
                .toList();
        return new IndexSnapshot(candidates.size(), stale.size(), documents, hasMore);
    }

    private int saveCompleted(List<IndexedDocument> completed) {
        List<UUID> ids = completed.stream().map(document -> document.snapshot().id()).toList();
        Map<UUID, Warehouse> current = new HashMap<>();
        warehouseRepository.findAllByIdInForUpdate(ids)
                .forEach(warehouse -> current.put(warehouse.getId(), warehouse));

        List<Warehouse> safeToSave = new ArrayList<>();
        for (IndexedDocument indexed : completed) {
            Warehouse warehouse = current.get(indexed.snapshot().id());
            if (warehouse == null || warehouse.isDeleted()
                    || !Objects.equals(indexed.snapshot().contentHash(),
                    WarehouseSearchDocumentSupport.contentHash(warehouse))
                    || !requiresReindex(warehouse)) {
                continue;
            }
            warehouse.setSearchEmbedding(Arrays.copyOf(
                    indexed.nativeVector(), indexed.nativeVector().length));
            warehouse.setSearchEmbeddingStr(indexed.serializedVector());
            warehouse.setSearchEmbeddingModel(indexed.model());
            warehouse.setSearchEmbeddingDimensions(Warehouse.SEARCH_EMBEDDING_DIMENSIONS);
            warehouse.setSearchContentHash(indexed.snapshot().contentHash());
            safeToSave.add(warehouse);
        }
        if (!safeToSave.isEmpty()) {
            warehouseRepository.saveAll(safeToSave);
        }
        return safeToSave.size();
    }

    private float[] toValidatedNativeVector(List<Float> vector) {
        if (vector == null || vector.size() != Warehouse.SEARCH_EMBEDDING_DIMENSIONS) {
            return null;
        }
        float[] result = new float[vector.size()];
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
        if (vector == null || vector.length != Warehouse.SEARCH_EMBEDDING_DIMENSIONS) {
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
        StringBuilder serialized = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                serialized.append(',');
            }
            serialized.append(vector[index]);
        }
        return serialized.append(']').toString();
    }

    private record IndexSnapshot(int scanned, int stale, List<DocumentSnapshot> documents,
                                 boolean hasMore) {
    }

    private record DocumentSnapshot(UUID id, String embeddingText, String contentHash) {
    }

    private record IndexedDocument(DocumentSnapshot snapshot, float[] nativeVector,
                                   String serializedVector, String model) {
    }

    public record ReindexResult(int scanned, int stale, int indexed, int failed, boolean hasMore) {
    }
}
