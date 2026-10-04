package fu.stockspace.stockspace_be.notification.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StompErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StompErrorHandler errorHandler =
            new StompErrorHandler(objectMapper);

    @Test
    void authenticationRejectionReturnsSafeStompErrorFrame() throws Exception {
        StompHeaderAccessor request = StompHeaderAccessor.create(StompCommand.CONNECT);
        request.setReceipt("connect-1");
        request.setLeaveMutable(true);
        Message<byte[]> requestMessage = MessageBuilder.createMessage(
                new byte[0],
                request.getMessageHeaders()
        );
        StompClientException failure = new StompClientException(
                requestMessage,
                "WS_AUTH_INVALID",
                "Invalid or expired WebSocket JWT",
                new IllegalArgumentException("internal parser detail")
        );

        Message<byte[]> response = errorHandler.handleClientMessageProcessingError(
                requestMessage,
                failure
        );

        assertNotNull(response);
        StompHeaderAccessor responseHeaders = MessageHeaderAccessor.getAccessor(
                response,
                StompHeaderAccessor.class
        );
        assertNotNull(responseHeaders);
        assertEquals(StompCommand.ERROR, responseHeaders.getCommand());
        assertEquals("connect-1", responseHeaders.getReceiptId());
        assertEquals(
                "WS_AUTH_INVALID: Invalid or expired WebSocket JWT",
                responseHeaders.getMessage()
        );

        JsonNode payload = objectMapper.readTree(response.getPayload());
        assertEquals("WS_AUTH_INVALID", payload.path("code").asText());
        assertEquals(
                "Invalid or expired WebSocket JWT",
                payload.path("message").asText()
        );
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalExceptionMessage() throws Exception {
        Message<byte[]> response = errorHandler.handleClientMessageProcessingError(
                null,
                new IllegalStateException("database host and credentials")
        );

        JsonNode payload = objectMapper.readTree(response.getPayload());
        assertEquals("WS_PROTOCOL_ERROR", payload.path("code").asText());
        assertEquals(
                "WebSocket request could not be processed",
                payload.path("message").asText()
        );
    }
}
