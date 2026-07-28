package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.repository.ChatMessageRepository;
import fu.stockspace.stockspace_be.chatbot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRetentionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Hard-deletes an old anonymous batch so TTL also bounds retained content,
     * not merely API access.
     */
    @Transactional
    public int purgeExpiredGuestBatch(Duration purgeAfter, int requestedBatchSize) {
        Duration grace = purgeAfter == null
                || purgeAfter.isNegative()
                ? Duration.ofDays(7)
                : purgeAfter;
        int batchSize = Math.max(1, Math.min(requestedBatchSize, 500));
        LocalDateTime cutoff = LocalDateTime.now().minus(grace);
        List<UUID> sessionIds = sessionRepository.findExpiredGuestSessionIds(
                cutoff,
                PageRequest.of(0, batchSize)
        );
        if (sessionIds.isEmpty()) {
            return 0;
        }

        messageRepository.deleteBySessionIds(sessionIds);
        return sessionRepository.deleteExpiredGuestSessions(sessionIds, cutoff);
    }
}
