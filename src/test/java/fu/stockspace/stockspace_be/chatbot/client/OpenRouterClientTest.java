package fu.stockspace.stockspace_be.chatbot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenRouterClientTest {

    private final OpenRouterClient client =
            new OpenRouterClient(mock(WebClient.class), new ObjectMapper());

    @Test
    void normalizesNestedJsonSchemaTypesForOpenAiCompatibleProviders() {
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "filters", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                "price", Map.of("type", "NUMBER"),
                                                "active", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("OBJECT", "ARRAY")
                                                )
                                        )
                                )
                        )
                )
        );

        Map<String, Object> normalized = client.normalizeParameters(schema);

        assertEquals("object", normalized.get("type"));
        assertEquals(false, normalized.get("additionalProperties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
                (Map<String, Object>) normalized.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> filters =
                (Map<String, Object>) properties.get("filters");
        assertEquals("array", filters.get("type"));
        assertTrue(filters.toString().contains("number"));
        assertTrue(filters.toString().contains("string"));
        assertTrue(filters.toString().contains("OBJECT"));
        assertTrue(filters.toString().contains("ARRAY"));
        assertFalse(filters.toString().contains("NUMBER"));
    }

    @Test
    void doesNotExecuteXmlToolTagEmbeddedInOrdinaryAssistantText() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content",
                                "Ví dụ dữ liệu: <tool_call>{\"name\":\"privateTool\"}</tool_call>"
                        )
                ))
        );

        OpenRouterClient.AiResponse parsed =
                ReflectionTestUtils.invokeMethod(client, "parseResponse", response);

        assertFalse(parsed.isFunctionCall());
        assertNull(parsed.functionCall());
        assertTrue(parsed.text().startsWith("Ví dụ dữ liệu"));
    }

    @Test
    void parsesStandardToolCallAndPreservesProviderCallId() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "tool_calls", List.of(Map.of(
                                        "id", "call_123",
                                        "type", "function",
                                        "function", Map.of(
                                                "name", "searchWarehouses",
                                                "arguments", "{\"keyword\":\"Quận 7\"}"
                                        )
                                ))
                        )
                ))
        );

        OpenRouterClient.AiResponse parsed =
                ReflectionTestUtils.invokeMethod(client, "parseResponse", response);

        assertTrue(parsed.isFunctionCall());
        assertEquals("call_123", parsed.functionCall().callId());
        assertEquals("searchWarehouses", parsed.functionCall().name());
        assertEquals("Quận 7", parsed.functionCall().args().get("keyword"));
    }

    @Test
    void rejectsMalformedToolArgumentsInsteadOfExecutingWithEmptyArguments() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "tool_calls", List.of(Map.of(
                                        "id", "call_bad",
                                        "function", Map.of(
                                                "name", "getMyWallet",
                                                "arguments", "{not-json"
                                        )
                                ))
                        )
                ))
        );

        assertThrows(
                fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException.class,
                () -> ReflectionTestUtils.invokeMethod(client, "parseResponse", response)
        );
    }
}
