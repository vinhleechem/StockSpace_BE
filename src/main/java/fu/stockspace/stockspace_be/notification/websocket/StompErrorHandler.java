package fu.stockspace.stockspace_be.notification.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private static final String GENERIC_CODE = "WS_PROTOCOL_ERROR";
    private static final String GENERIC_MESSAGE =
            "WebSocket request could not be processed";

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception) {
        StompClientException clientException = findClientException(exception);
        String code = clientException == null
                ? GENERIC_CODE
                : clientException.getCode();
        String publicMessage = clientException == null
                ? GENERIC_MESSAGE
                : clientException.getClientMessage();

        StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
        error.setMessage(code + ": " + publicMessage);
        error.setContentType(MimeTypeUtils.APPLICATION_JSON);
        error.setLeaveMutable(true);
        copyReceipt(clientMessage, error);

        if (clientException == null) {
            log.warn("[WebSocket] Unhandled STOMP error code={} type={}",
                    code,
                    exception.getClass().getSimpleName());
        }

        return MessageBuilder.createMessage(
                errorPayload(code, publicMessage),
                error.getMessageHeaders()
        );
    }

    private void copyReceipt(Message<byte[]> clientMessage,
                             StompHeaderAccessor error) {
        if (clientMessage == null) {
            return;
        }
        StompHeaderAccessor request = MessageHeaderAccessor.getAccessor(
                clientMessage,
                StompHeaderAccessor.class
        );
        if (request != null && request.getReceipt() != null) {
            error.setReceiptId(request.getReceipt());
        }
    }

    private byte[] errorPayload(String code, String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            log.error("[WebSocket] Could not serialize STOMP error payload type={}",
                    exception.getClass().getSimpleName());
            return ("{\"code\":\"" + GENERIC_CODE
                    + "\",\"message\":\"" + GENERIC_MESSAGE + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private StompClientException findClientException(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof StompClientException clientException) {
                return clientException;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
