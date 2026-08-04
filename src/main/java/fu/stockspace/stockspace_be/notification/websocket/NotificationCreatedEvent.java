package fu.stockspace.stockspace_be.notification.websocket;

import fu.stockspace.stockspace_be.notification.dto.NotificationResponse;

/** Event emitted when a notification has been persisted for a user. */
public record NotificationCreatedEvent(String recipientEmail, NotificationResponse notification) {
}
