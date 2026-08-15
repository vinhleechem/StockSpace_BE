package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.notification.dto.NotificationResponse;


public record NotificationCreatedEvent(String recipientEmail, NotificationResponse notification) {
}
