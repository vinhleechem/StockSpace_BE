package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.repository.ChatMessageRepository;
import fu.stockspace.stockspace_be.chatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRetentionServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Test
    void deletesMessagesBeforeExpiredGuestSessionsInBoundedBatch() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(sessionRepository.findExpiredGuestSessionIds(
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(ids);
        when(sessionRepository.deleteExpiredGuestSessions(
                org.mockito.ArgumentMatchers.eq(ids),
                any(LocalDateTime.class)
        )).thenReturn(2);
        ChatRetentionService service =
                new ChatRetentionService(sessionRepository, messageRepository);

        int purged = service.purgeExpiredGuestBatch(Duration.ofDays(7), 200);

        assertEquals(2, purged);
        var ordered = inOrder(messageRepository, sessionRepository);
        ordered.verify(messageRepository).deleteBySessionIds(ids);
        ordered.verify(sessionRepository).deleteExpiredGuestSessions(
                org.mockito.ArgumentMatchers.eq(ids),
                any(LocalDateTime.class)
        );
    }
}
