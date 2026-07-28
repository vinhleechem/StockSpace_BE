package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient;
import fu.stockspace.stockspace_be.chatbot.dto.ChatMessageResponse;
import fu.stockspace.stockspace_be.chatbot.dto.ChatSessionResponse;
import fu.stockspace.stockspace_be.chatbot.entity.ChatMessage;
import fu.stockspace.stockspace_be.chatbot.entity.ChatSession;
import fu.stockspace.stockspace_be.chatbot.repository.ChatMessageRepository;
import fu.stockspace.stockspace_be.chatbot.repository.ChatSessionRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Owns chat persistence and keeps database transactions short. No external
 * provider request is ever made from this class.
 */
@Service
@RequiredArgsConstructor
public class ChatConversationStore {

    private static final int TITLE_MAX_CODE_POINTS = 50;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Value("${app.chatbot.guest-session-ttl:24h}")
    private Duration guestSessionTtl;

    @Transactional
    public PreparedChatSession prepareUserSession(UUID userId, String rawSessionId) {
        ChatSession session;
        if (rawSessionId != null && !rawSessionId.isBlank()) {
            UUID sessionId = parseSessionId(rawSessionId);
            session = sessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.CHAT_SESSION_NOT_FOUND));
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            session = sessionRepository.save(ChatSession.builder().user(user).build());
        }

        return new PreparedChatSession(session.getId(), null, buildHistory(session.getId()));
    }

    /**
     * A missing token creates a server-minted session. A supplied unknown token
     * is rejected instead of being used to create a client-chosen session.
     */
    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public PreparedChatSession prepareGuestSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            String newToken = UUID.randomUUID().toString();
            ChatSession session = sessionRepository.save(ChatSession.builder()
                    .sessionToken(hashToken(newToken))
                    .expiresAt(nextGuestExpiry())
                    .build());
            return new PreparedChatSession(session.getId(), newToken, List.of());
        }

        String canonicalToken = canonicalGuestToken(rawToken);
        String tokenHash = hashToken(canonicalToken);
        ChatSession session = sessionRepository.findBySessionTokenAndIsDeletedFalse(tokenHash)
                .or(() -> sessionRepository.findBySessionTokenAndIsDeletedFalse(canonicalToken))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        ensureGuestSessionActive(session);

        // Transparently migrate legacy plaintext tokens.
        if (!tokenHash.equals(session.getSessionToken())) {
            session.setSessionToken(tokenHash);
        }
        session.setExpiresAt(nextGuestExpiry());
        sessionRepository.save(session);

        return new PreparedChatSession(
                session.getId(),
                canonicalToken,
                buildHistory(session.getId())
        );
    }

    @Transactional
    public LocalDateTime appendUserTurn(UUID userId,
                                        UUID sessionId,
                                        String userMessage,
                                        String assistantMessage) {
        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        return appendTurn(session, userMessage, assistantMessage);
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public LocalDateTime appendGuestTurn(String rawToken,
                                         UUID sessionId,
                                         String userMessage,
                                         String assistantMessage) {
        String canonicalToken = canonicalGuestToken(rawToken);
        String tokenHash = hashToken(canonicalToken);
        ChatSession session = sessionRepository
                .findGuestByIdAndTokenForUpdate(sessionId, tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        ensureGuestSessionActive(session);
        session.setExpiresAt(nextGuestExpiry());
        return appendTurn(session, userMessage, assistantMessage);
    }

    @Transactional(readOnly = true)
    public Page<ChatSessionResponse> getMySessions(UUID userId, Pageable pageable) {
        return sessionRepository.findByUserIdAndNotDeleted(userId, pageable)
                .map(session -> new ChatSessionResponse(
                        session.getId(),
                        session.getTitle(),
                        session.getCreatedAt(),
                        session.getUpdatedAt()
                ));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSessionMessages(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        return recentMessagesChronological(session.getId());
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        session.setDeleted(true);
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public List<ChatMessageResponse> getGuestHistory(String rawToken) {
        String canonicalToken = canonicalGuestToken(rawToken);
        String tokenHash = hashToken(canonicalToken);
        ChatSession session = sessionRepository.findBySessionTokenAndIsDeletedFalse(tokenHash)
                .or(() -> sessionRepository.findBySessionTokenAndIsDeletedFalse(canonicalToken))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        ensureGuestSessionActive(session);
        if (!tokenHash.equals(session.getSessionToken())) {
            session.setSessionToken(tokenHash);
        }
        return recentMessagesChronological(session.getId());
    }

    private LocalDateTime appendTurn(ChatSession session,
                                     String userMessage,
                                     String assistantMessage) {
        ChatMessage user = messageRepository.save(ChatMessage.builder()
                .session(session)
                .role("user")
                .content(userMessage)
                .build());
        ChatMessage assistant = messageRepository.save(ChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(assistantMessage)
                .build());
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(truncateByCodePoint(userMessage, TITLE_MAX_CODE_POINTS));
        }
        sessionRepository.save(session);
        return assistant.getCreatedAt() != null ? assistant.getCreatedAt() : LocalDateTime.now();
    }

    private List<java.util.Map<String, Object>> buildHistory(UUID sessionId) {
        List<ChatMessage> messages = new ArrayList<>(
                messageRepository
                        .findTop10BySession_IdAndIsDeletedFalseOrderByCreatedAtDesc(sessionId)
        );
        Collections.reverse(messages);
        return messages.stream()
                .map(message -> OpenRouterClient.buildContent(
                        message.getRole(),
                        message.getContent()
                ))
                .toList();
    }

    private List<ChatMessageResponse> recentMessagesChronological(UUID sessionId) {
        List<ChatMessage> messages = new ArrayList<>(
                messageRepository
                        .findTop200BySession_IdAndIsDeletedFalseOrderByCreatedAtDesc(sessionId)
        );
        Collections.reverse(messages);
        return messages.stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    private UUID parseSessionId(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
    }

    private String canonicalGuestToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        try {
            UUID parsed = UUID.fromString(token);
            String canonical = parsed.toString();
            if (!canonical.equalsIgnoreCase(token)) {
                throw new IllegalArgumentException("Non-canonical token");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Guest session token không hợp lệ");
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void ensureGuestSessionActive(ChatSession session) {
        if (session.getExpiresAt() != null
                && !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            session.setDeleted(true);
            throw new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (session.getExpiresAt() == null) {
            session.setExpiresAt(nextGuestExpiry());
        }
    }

    private LocalDateTime nextGuestExpiry() {
        Duration ttl = guestSessionTtl == null || guestSessionTtl.isNegative()
                || guestSessionTtl.isZero()
                ? Duration.ofHours(24)
                : guestSessionTtl;
        return LocalDateTime.now().plus(ttl);
    }

    private String truncateByCodePoint(String text, int maxCodePoints) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= maxCodePoints) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, endIndex) + "...";
    }
}
