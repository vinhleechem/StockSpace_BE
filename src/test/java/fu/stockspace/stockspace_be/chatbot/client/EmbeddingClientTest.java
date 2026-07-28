package fu.stockspace.stockspace_be.chatbot.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingClientTest {

    @Test
    void missingApiKeyReturnsPositionPreservingFallbackWithoutNetworkCall() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.error(new AssertionError("Network must not be called"));
                })
                .build();
        EmbeddingClient client = new EmbeddingClient(
                webClient,
                "",
                "test-model",
                2,
                500
        );

        List<List<Float>> result = client.getEmbeddings(List.of("first", "", "second"));

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(List::isEmpty));
        assertEquals(0, calls.get());
        assertNull(client.toVectorString(List.of()));
    }

    @Test
    void batchResponseUsesProviderIndexesAndValidatesDimensions() {
        String responseBody = """
                {
                  "data": [
                    {"index": 1, "embedding": [0.0, 1.0]},
                    {"index": 0, "embedding": [1.0, 0.0]}
                  ]
                }
                """;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body(responseBody)
                                .build()
                ))
                .build();
        EmbeddingClient client = new EmbeddingClient(
                webClient,
                "key",
                "test-model",
                2,
                1_000
        );

        List<List<Float>> result = client.getEmbeddings(List.of("first", "second"));

        assertEquals(List.of(1.0f, 0.0f), result.get(0));
        assertEquals(List.of(0.0f, 1.0f), result.get(1));
    }

    @Test
    void providerFailureReturnsEmptyVectorsForWholeBatch() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.error(new RuntimeException("plaintext must not be logged"));
                })
                .build();
        EmbeddingClient client = new EmbeddingClient(
                webClient,
                "key",
                "test-model",
                2,
                500
        );

        List<List<Float>> result = client.getEmbeddings(List.of("first", "second"));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(List::isEmpty));
        assertEquals(1, calls.get());
    }

    @Test
    void inputSpecificBatchFailureIsBisectedSoOnePoisonDocumentCannotBlockOthers() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int call = calls.incrementAndGet();
                    if (call <= 2) {
                        return Mono.just(
                                ClientResponse.create(HttpStatus.BAD_REQUEST)
                                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                        .body("{\"error\":\"invalid input\"}")
                                        .build()
                        );
                    }
                    return Mono.just(
                            ClientResponse.create(HttpStatus.OK)
                                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                    .body("""
                                            {
                                              "data": [
                                                {"index": 0, "embedding": [0.0, 1.0]}
                                              ]
                                            }
                                            """)
                                    .build()
                    );
                })
                .build();
        EmbeddingClient client = new EmbeddingClient(
                webClient,
                "key",
                "test-model",
                2,
                1_000
        );

        List<List<Float>> result = client.getEmbeddings(List.of("poison", "healthy"));

        assertTrue(result.get(0).isEmpty());
        assertEquals(List.of(0.0f, 1.0f), result.get(1));
        assertEquals(3, calls.get());
    }
}
