package fu.stockspace.stockspace_be.chatbot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;








class StreamAccumulatorTest {

    private final OpenRouterClient client =
            new OpenRouterClient(mock(WebClient.class), new ObjectMapper());


    private Object newAccumulator(java.util.function.Consumer<String> onDelta) {
        try {
            Class<?> clazz = Class.forName(
                    "fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient$StreamAccumulator");
            var ctor = clazz.getDeclaredConstructor(OpenRouterClient.class,
                    java.util.function.Consumer.class);
            ctor.setAccessible(true);
            return ctor.newInstance(client, onDelta);
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate StreamAccumulator", e);
        }
    }

    private void accept(Object acc, String data) {
        ReflectionTestUtils.invokeMethod(acc, "accept", data);
    }

    private OpenRouterClient.AiResponse finish(Object acc) {
        return ReflectionTestUtils.invokeMethod(acc, "finish");
    }



    @Test
    void assemblesTextDeltasAndFiresCallbackForEachChunk() {
        List<String> received = new ArrayList<>();
        Object acc = newAccumulator(received::add);

        accept(acc, "{\"choices\":[{\"delta\":{\"content\":\"Xin \"}}]}");
        accept(acc, "{\"choices\":[{\"delta\":{\"content\":\"chào!\"}}]}");
        accept(acc, "[DONE]");

        OpenRouterClient.AiResponse response = finish(acc);

        assertFalse(response.isFunctionCall());
        assertEquals("Xin chào!", response.text());
        assertEquals(List.of("Xin ", "chào!"), received);
    }

    @Test
    void ignoresDoneMarkerAndBlankLines() {
        Object acc = newAccumulator(null);

        accept(acc, "  ");
        accept(acc, "[DONE]");
        accept(acc, "{\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}");


        OpenRouterClient.AiResponse response = finish(acc);
        assertEquals("OK", response.text());
    }

    @Test
    void assemblesToolCallFromMultipleChunks() {
        Object acc = newAccumulator(null);


        accept(acc, """
                {"choices":[{"delta":{"tool_calls":[{
                  "id":"call_abc",
                  "function":{"name":"searchWarehouses","arguments":"{\\"keyword\\":\\""}
                }]}}]}""");

        accept(acc, """
                {"choices":[{"delta":{"tool_calls":[{
                  "function":{"arguments":"Quận 7\\"}"}
                }]}}]}""");
        accept(acc, "[DONE]");

        OpenRouterClient.AiResponse response = finish(acc);

        assertTrue(response.isFunctionCall());
        assertEquals("call_abc", response.functionCall().callId());
        assertEquals("searchWarehouses", response.functionCall().name());
        assertEquals("Quận 7", response.functionCall().args().get("keyword"));
    }

    @Test
    void generatesRandomCallIdWhenProviderOmitsIt() {
        Object acc = newAccumulator(null);

        accept(acc, """
                {"choices":[{"delta":{"tool_calls":[{
                  "function":{"name":"getMyWallet","arguments":"{}"}
                }]}}]}""");

        OpenRouterClient.AiResponse response = finish(acc);

        assertTrue(response.isFunctionCall());
        assertEquals("getMyWallet", response.functionCall().name());
        assertNotNull(response.functionCall().callId());
        assertTrue(response.functionCall().callId().startsWith("call_"));
    }

    @Test
    void throwsChatProviderExceptionOnProviderRateLimitError() {
        Object acc = newAccumulator(null);

        assertThrows(OpenRouterClient.StreamConsumerException.class,
                () -> accept(acc, "{\"error\":{\"code\":429,\"message\":\"rate limit\"}}"));
    }

    @Test
    void throwsChatProviderExceptionOnGenericProviderError() {
        Object acc = newAccumulator(null);

        assertThrows(OpenRouterClient.StreamConsumerException.class,
                () -> accept(acc, "{\"error\":{\"code\":500,\"message\":\"server error\"}}"));
    }

    @Test
    void throwsChatProviderExceptionOnFinishWithEmptyText() {
        Object acc = newAccumulator(null);

        accept(acc, "[DONE]");

        assertThrows(ChatProviderException.class, () -> finish(acc));
    }

    @Test
    void skipsChunksWithMalformedJson() {
        Object acc = newAccumulator(null);

        accept(acc, "not-json-at-all");
        accept(acc, "{\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}");

        OpenRouterClient.AiResponse response = finish(acc);
        assertEquals("OK", response.text());
    }

    @Test
    void skipsChunksWithMissingChoices() {
        Object acc = newAccumulator(null);

        accept(acc, "{\"id\":\"chatcmpl-xyz\"}");
        accept(acc, "{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}");

        OpenRouterClient.AiResponse response = finish(acc);
        assertEquals("Hi", response.text());
    }

    @Test
    void streamConsumerExceptionUnwrapsCorrectly() {
        ChatProviderException inner =
                new ChatProviderException(
                        fu.stockspace.stockspace_be.common.exception.ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        OpenRouterClient.StreamConsumerException wrapped =
                new OpenRouterClient.StreamConsumerException(inner);

        assertSame(inner, wrapped.unwrap());
    }
}
