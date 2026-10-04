package fu.stockspace.stockspace_be.notification.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;

final class StompClientException extends MessageDeliveryException {

    private final String code;
    private final String clientMessage;

    StompClientException(Message<?> failedMessage,
                         String code,
                         String clientMessage,
                         Throwable cause) {
        super(failedMessage, clientMessage, cause);
        this.code = code;
        this.clientMessage = clientMessage;
    }

    String getCode() {
        return code;
    }

    String getClientMessage() {
        return clientMessage;
    }
}
