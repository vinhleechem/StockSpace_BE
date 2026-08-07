package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexServiceTest {

    @Mock
    private SystemKnowledgeRepository knowledgeRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private KnowledgeIndexService indexService;

    @BeforeEach
    void setUp() {
        indexService = new KnowledgeIndexService(
                knowledgeRepository,
                embeddingClient,
                transactionTemplate
        );
        lenient().when(embeddingClient.getEmbeddingModel()).thenReturn("model-v2");
        lenient().when(embeddingClient.getEmbeddingDimensions())
                .thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void reindexesOnlyStaleDocumentsAndPersistsFreshMetadata() {
        SystemKnowledge fresh = document("kb.fresh", "Fresh", "Fresh content");
        float[] freshVector = nativeVector(1.0f, 0.0f);
        fresh.setEmbeddingVector(freshVector);
        fresh.setEmbeddingStr(vectorString(freshVector));
        fresh.setEmbeddingModel("model-v2");
        fresh.setEmbeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS);
        fresh.setContentHash(KnowledgeDocumentSupport.contentHash(fresh));

        SystemKnowledge stale = document("kb.stale", "Stale", "Changed content");
        float[] staleVector = nativeVector(0.0f, 1.0f);
        stale.setEmbeddingVector(staleVector);
        stale.setEmbeddingStr(vectorString(staleVector));
        stale.setEmbeddingModel("model-v1");
        stale.setEmbeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS);
        stale.setContentHash(KnowledgeDocumentSupport.contentHash(stale));

        SystemKnowledge missing = document("kb.missing", "Missing", "No vector");

        when(knowledgeRepository.findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        ))
                .thenReturn(List.of(stale, missing));
        when(embeddingClient.getEmbeddings(
                any(),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS)
        )).thenReturn(List.of(
                vector(0.5f, 0.5f),
                vector(0.2f, 0.8f)
        ));
        when(knowledgeRepository.findAllByIdInForUpdate(any()))
                .thenReturn(List.of(stale, missing));

        KnowledgeIndexService.ReindexResult result = indexService.reindexBatch(10);

        assertEquals(2, result.scanned());
        assertEquals(2, result.stale());
        assertEquals(2, result.indexed());
        assertEquals(0, result.failed());
        assertFalse(result.hasMore());
        assertEquals("model-v2", stale.getEmbeddingModel());
        assertEquals(SystemKnowledge.EMBEDDING_DIMENSIONS, stale.getEmbeddingDimensions());
        assertEquals(KnowledgeDocumentSupport.contentHash(stale), stale.getContentHash());
        assertArrayEquals(nativeVector(0.5f, 0.5f), stale.getEmbeddingVector());
        assertEquals(vectorString(nativeVector(0.5f, 0.5f)), stale.getEmbeddingStr());
        verify(knowledgeRepository).saveAll(any());
    }

    @Test
    void failedEmbeddingIsNotStoredAsEmptyVector() {
        SystemKnowledge missing = document("kb.missing", "Missing", "No vector");
        when(knowledgeRepository.findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        ))
                .thenReturn(List.of(missing));
        when(embeddingClient.getEmbeddings(
                any(),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS)
        ))
                .thenReturn(List.of(List.of()));

        KnowledgeIndexService.ReindexResult result = indexService.reindexBatch(5);

        assertEquals(0, result.indexed());
        assertEquals(1, result.failed());
        assertNull(missing.getEmbeddingStr());
        assertNull(missing.getEmbeddingVector());
        verify(knowledgeRepository, never()).saveAll(any());
        verify(knowledgeRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void contentChangedDuringRemoteCallIsSkippedOnSave() {
        SystemKnowledge snapshotDocument = document("kb.policy", "Policy", "Original content");
        SystemKnowledge concurrentlyChanged = documentWithId(
                snapshotDocument.getId(),
                "kb.policy",
                "Policy",
                "New content"
        );
        when(knowledgeRepository.findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        ))
                .thenReturn(List.of(snapshotDocument));
        when(embeddingClient.getEmbeddings(
                any(),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS)
        )).thenReturn(List.of(vector(1.0f, 0.0f)));
        when(knowledgeRepository.findAllByIdInForUpdate(any()))
                .thenReturn(List.of(concurrentlyChanged));

        KnowledgeIndexService.ReindexResult result = indexService.reindexBatch(5);

        assertEquals(0, result.indexed());
        assertEquals(1, result.failed());
        assertNull(concurrentlyChanged.getEmbeddingStr());
        assertNull(concurrentlyChanged.getEmbeddingVector());
        verify(knowledgeRepository, never()).saveAll(any());
    }

    @Test
    void batchSizeIsBoundedAndReportsMoreStaleDocuments() {
        List<SystemKnowledge> documents = java.util.stream.IntStream.range(0, 65)
                .mapToObj(index -> document("kb." + index, "Title " + index, "Content " + index))
                .toList();
        List<List<Float>> embeddings = java.util.stream.IntStream.range(0, 64)
                .mapToObj(index -> vector(1.0f, 0.0f))
                .toList();
        when(knowledgeRepository.findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        )).thenReturn(documents);
        when(embeddingClient.getEmbeddings(
                org.mockito.ArgumentMatchers.argThat(inputs -> inputs.size() == 64),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS)
        )).thenReturn(embeddings);
        when(knowledgeRepository.findAllByIdInForUpdate(any()))
                .thenReturn(documents.subList(0, 64));

        KnowledgeIndexService.ReindexResult result = indexService.reindexBatch(1_000);

        assertEquals(65, result.stale());
        assertEquals(64, result.indexed());
        assertTrue(result.hasMore());
        verify(knowledgeRepository).findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 65)
        );
    }

    @Test
    void rejectsWrongDimensionNonFiniteAndZeroNormEmbeddings() {
        List<SystemKnowledge> documents = List.of(
                document("kb.wrong-dimension", "Wrong", "Wrong dimension"),
                document("kb.non-finite", "Non finite", "Non finite vector"),
                document("kb.zero", "Zero", "Zero norm vector")
        );
        List<Float> nonFinite = new ArrayList<>(vector(1.0f, 0.0f));
        nonFinite.set(0, Float.NaN);

        when(knowledgeRepository.findStaleIndexCandidates(
                eq("model-v2"),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS),
                any(Pageable.class)
        )).thenReturn(documents);
        when(embeddingClient.getEmbeddings(
                any(),
                eq(SystemKnowledge.EMBEDDING_DIMENSIONS)
        )).thenReturn(List.of(
                List.of(1.0f, 0.0f),
                nonFinite,
                Collections.nCopies(SystemKnowledge.EMBEDDING_DIMENSIONS, 0.0f)
        ));

        KnowledgeIndexService.ReindexResult result = indexService.reindexBatch(10);

        assertEquals(0, result.indexed());
        assertEquals(3, result.failed());
        verify(knowledgeRepository, never()).findAllByIdInForUpdate(any());
        verify(knowledgeRepository, never()).saveAll(any());
    }

    @Test
    void changingContentClearsNativeRollbackAndCompatibilityMetadata() {
        SystemKnowledge knowledge = document("kb.policy", "Policy", "Original");
        knowledge.setEmbeddingVector(nativeVector(1.0f, 0.0f));
        knowledge.setEmbeddingStr(vectorString(knowledge.getEmbeddingVector()));
        knowledge.setEmbeddingModel("model-v2");
        knowledge.setEmbeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS);
        knowledge.setContentHash(KnowledgeDocumentSupport.contentHash(knowledge));

        knowledge.setContent("Changed");

        assertNull(knowledge.getEmbeddingVector());
        assertNull(knowledge.getEmbeddingStr());
        assertNull(knowledge.getEmbeddingModel());
        assertNull(knowledge.getEmbeddingDimensions());
        assertNull(knowledge.getContentHash());
    }

    @Test
    void rejectsProviderDimensionsThatDoNotMatchPgVectorSchema() {
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(768);

        assertThrows(
                IllegalStateException.class,
                () -> indexService.reindexBatch(10)
        );
        verify(knowledgeRepository, never()).findStaleIndexCandidates(any(), anyInt(), any());
        verify(embeddingClient, never()).getEmbeddings(any(), anyInt());
    }

    private SystemKnowledge document(String sourceId, String title, String content) {
        return documentWithId(UUID.randomUUID(), sourceId, title, content);
    }

    private SystemKnowledge documentWithId(
            UUID id,
            String sourceId,
            String title,
            String content
    ) {
        return SystemKnowledge.builder()
                .id(id)
                .category(KnowledgeCategory.POLICY)
                .sourceId(sourceId)
                .title(title)
                .content(content)
                .isActive(true)
                .isDeleted(false)
                .build();
    }

    private List<Float> vector(float first, float second) {
        List<Float> vector = new ArrayList<>(
                Collections.nCopies(SystemKnowledge.EMBEDDING_DIMENSIONS, 0.0f)
        );
        vector.set(0, first);
        vector.set(1, second);
        return List.copyOf(vector);
    }

    private float[] nativeVector(float first, float second) {
        float[] vector = new float[SystemKnowledge.EMBEDDING_DIMENSIONS];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private String vectorString(float[] vector) {
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
}
