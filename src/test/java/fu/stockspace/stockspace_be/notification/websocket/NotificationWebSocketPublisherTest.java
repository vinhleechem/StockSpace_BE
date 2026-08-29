package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.notification.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationWebSocketPublisher publisher;

    @Test
    void publish_SendsNotificationToTheRecipientPrivateQueue() {
        NotificationResponse notification = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .title("Contract submitted")
                .build();

        publisher.publish(new NotificationCreatedEvent("tenant@test.com", notification));

        verify(messagingTemplate).convertAndSendToUser(
                "tenant@test.com",
                "/queue/notifications",
                notification
        );
    }
}
