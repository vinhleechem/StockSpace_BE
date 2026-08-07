package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.chatbot.entity.ChatSession;
import fu.stockspace.stockspace_be.chatbot.repository.ChatMessageRepository;
import fu.stockspace.stockspace_be.chatbot.repository.ChatSessionRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationStoreTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    private ChatConversationStore store;

    @BeforeEach
    void setUp() {
        store = new ChatConversationStore(
                sessionRepository,
                messageRepository,
                userRepository
        );
        ReflectionTestUtils.setField(store, "guestSessionTtl", Duration.ofHours(24));
    }

    @Test
    void newGuestSessionUsesServerMintedTokenAndPersistsOnlyItsHash() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(sessionId);
            return session;
        });

        PreparedChatSession prepared = store.prepareGuestSession(null);

        assertEquals(sessionId, prepared.sessionId());
        assertNotNull(prepared.guestToken());
        assertDoesNotThrow(() -> UUID.fromString(prepared.guestToken()));

        ArgumentCaptor<ChatSession> saved =
                ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionRepository).save(saved.capture());
        String storedToken = saved.getValue().getSessionToken();
        assertEquals(64, storedToken.length());
        assertNotEquals(prepared.guestToken(), storedToken);
        assertFalse(storedToken.contains("-"));
        assertNotNull(saved.getValue().getExpiresAt());
    }

    @Test
    void unknownClientChosenGuestTokenIsRejectedInsteadOfCreatingSession() {
        String clientChosenToken = UUID.randomUUID().toString();
        when(sessionRepository.findBySessionTokenAndIsDeletedFalse(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> store.prepareGuestSession(clientChosenToken)
        );

        verify(sessionRepository, never()).save(any(ChatSession.class));
    }
}
