package fu.stockspace.stockspace_be.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends persisted notifications to the matching authenticated STOMP user. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketPublisher {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(NotificationCreatedEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                    event.recipientEmail(),
                    DESTINATION,
                    event.notification()
            );
        } catch (Exception exception) {
            // Realtime delivery must not turn a successfully committed business action into a failed API call.
            log.error("Could not deliver notification {} to WebSocket user {}",
                    event.notification().getId(), event.recipientEmail(), exception);
        }
    }
}
