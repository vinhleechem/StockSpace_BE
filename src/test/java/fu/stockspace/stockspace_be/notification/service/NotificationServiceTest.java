package fu.stockspace.stockspace_be.notification.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.notification.dto.PagedNotificationResponse;
import fu.stockspace.stockspace_be.notification.entity.Notification;
import fu.stockspace.stockspace_be.notification.repository.NotificationRepository;
import fu.stockspace.stockspace_be.notification.websocket.NotificationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testPush_Success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@test.com").build();
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.push(userId, "Test Title", "Test Message", "SYSTEM");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("Test Title", saved.getTitle());
        assertEquals("Test Message", saved.getMessage());
        assertEquals("SYSTEM", saved.getType());
        assertFalse(saved.isRead());
        verify(applicationEventPublisher).publishEvent((Object) argThat(
                (NotificationCreatedEvent event) -> "user@test.com".equals(event.recipientEmail())
                        && "Test Title".equals(event.notification().getTitle())
        ));
    }

    @Test
    void testPush_UserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
                notificationService.push(userId, "Title", "Message", "SYSTEM")
        );
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void testGetMyNotifications_Success() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        User user = User.builder().id(userId).build();

        Notification n1 = Notification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("T1")
                .message("M1")
                .type("SYSTEM")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        Page<Notification> page = new PageImpl<>(List.of(n1), pageable, 1);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

        PagedNotificationResponse response = notificationService.getMyNotifications(userId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("T1", response.getContent().get(0).getTitle());
        assertFalse(response.getContent().get(0).isRead());
        assertEquals(0, response.getPage());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void testMarkAsRead_Success() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        Notification notification = Notification.builder()
                .id(notificationId)
                .user(user)
                .isRead(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(userId, notificationId);

        assertTrue(notification.isRead());
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void testMarkAsRead_Forbidden() {
        UUID userId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        User anotherUser = User.builder().id(anotherUserId).build();
        Notification notification = Notification.builder()
                .id(notificationId)
                .user(anotherUser)
                .isRead(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThrows(ForbiddenException.class, () -> 
                notificationService.markAsRead(userId, notificationId)
        );
        assertFalse(notification.isRead());
        verify(notificationRepository, never()).save(notification);
    }

    @Test
    void testMarkAsRead_NotFound() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
                notificationService.markAsRead(userId, notificationId)
        );
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void testMarkAllAsRead_Success() {
        UUID userId = UUID.randomUUID();

        notificationService.markAllAsRead(userId);

        verify(notificationRepository, times(1)).markAllAsRead(userId);
    }

    @Test
    void testCountUnread_Success() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

        long count = notificationService.countUnread(userId);

        assertEquals(5L, count);
        verify(notificationRepository, times(1)).countByUserIdAndIsReadFalse(userId);
    }
}
