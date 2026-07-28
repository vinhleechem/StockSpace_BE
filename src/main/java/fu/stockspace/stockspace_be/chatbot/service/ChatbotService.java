package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient;
import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient.AiResponse;
import fu.stockspace.stockspace_be.chatbot.dto.*;
import fu.stockspace.stockspace_be.chatbot.entity.ChatMessage;
import fu.stockspace.stockspace_be.chatbot.entity.ChatSession;
import fu.stockspace.stockspace_be.chatbot.repository.ChatMessageRepository;
import fu.stockspace.stockspace_be.chatbot.repository.ChatSessionRepository;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatToolRegistry;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChatbotService — xử lý toàn bộ logic chatbot bao gồm Agentic Loop.
 *
 * Agentic Loop (tối đa 5 vòng):
 *   OpenRouter AI → FUNCTION_CALL → execute tool → gửi kết quả về OpenRouter → lặp cho đến khi TEXT
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatbotService {

    private static final int MAX_AGENTIC_ITERATIONS = 5;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final OpenRouterClient openRouterClient;
    private final ChatToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final UserRepository userRepository;

    // ── Authenticated User ────────────────────────────────────────────────────

    /**
     * Xử lý tin nhắn từ user đã đăng nhập.
     * Auto-detect role từ JWT (truyền vào roleName).
     *
     * @param userId   UUID của user
     * @param roleName Role name từ JWT (VD: "ROLE_TENANT")
     * @param request  Request chứa sessionId (optional) và message
     */
    public ChatResponse processMessage(UUID userId, String roleName, SendMessageRequest request) {
        // 1. Load/tạo session
        ChatSession session = resolveUserSession(userId, request.sessionId());
        boolean isNewSession = session.getTitle() == null;

        // 2. Lấy 10 tin nhắn gần nhất làm context history
        List<Map<String, Object>> history = buildHistory(session.getId());

        // 3. Build system prompt + tools theo role
        String systemPrompt = promptBuilder.buildSystemPrompt(roleName);
        List<ChatTool> tools = toolRegistry.getToolsForRole(roleName);

        // 4. Agentic loop
        String botReply = runAgenticLoop(history, systemPrompt, request.message(), tools, userId);

        // 5. Lưu tin nhắn vào DB
        saveMessage(session, "user", request.message());
        saveMessage(session, "assistant", botReply);

        // 6. Set title nếu session mới
        if (isNewSession) {
            session.setTitle(truncate(request.message(), 50));
        }
        session = sessionRepository.save(session);

        return new ChatResponse(session.getId(), null, botReply, LocalDateTime.now());
    }

    // ── Guest ─────────────────────────────────────────────────────────────────

    /**
     * Xử lý tin nhắn từ GUEST (không cần đăng nhập).
     * Role cố định = GUEST, tools chỉ gồm public warehouse tools.
     *
     * @param sessionToken  Token định danh guest session (null để tạo mới)
     * @param request       Request chứa message
     */
    public ChatResponse processGuestMessage(String sessionToken, SendMessageRequest request) {
        // Tạo token mới nếu chưa có
        String token = (sessionToken != null && !sessionToken.isBlank())
                ? sessionToken
                : UUID.randomUUID().toString();

        // Load/tạo guest session
        ChatSession session = sessionRepository.findBySessionTokenAndIsDeletedFalse(token)
                .orElseGet(() -> {
                    ChatSession newSession = ChatSession.builder()
                            .sessionToken(token)
                            .build();
                    return sessionRepository.save(newSession);
                });

        boolean isNewSession = session.getTitle() == null;

        // History + tools cho GUEST
        List<Map<String, Object>> history = buildHistory(session.getId());
        String systemPrompt = promptBuilder.buildSystemPrompt("GUEST");
        List<ChatTool> tools = toolRegistry.getToolsForRole("GUEST");

        // Agentic loop — userId = null cho GUEST
        String botReply = runAgenticLoop(history, systemPrompt, request.message(), tools, null);

        // Lưu tin nhắn
        saveMessage(session, "user", request.message());
        saveMessage(session, "assistant", botReply);

        if (isNewSession) {
            session.setTitle(truncate(request.message(), 50));
        }
        session = sessionRepository.save(session);

        return new ChatResponse(session.getId(), token, botReply, LocalDateTime.now());
    }

    // ── Session Management ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ChatSessionResponse> getMySessions(UUID userId, Pageable pageable) {
        return sessionRepository.findByUserIdAndNotDeleted(userId, pageable)
                .map(s -> new ChatSessionResponse(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSessionMessages(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return messageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        session.setDeleted(true);
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getGuestHistory(String sessionToken) {
        ChatSession session = sessionRepository.findBySessionTokenAndIsDeletedFalse(sessionToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        return messageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .collect(Collectors.toList());
    }

    // ── Agentic Loop ──────────────────────────────────────────────────────────

    /**
     * Vòng lặp agentic: gọi OpenRouter AI → nếu FUNCTION_CALL → execute → gửi kết quả lại → lặp.
     * Tối đa MAX_AGENTIC_ITERATIONS vòng.
     */
    private String runAgenticLoop(List<Map<String, Object>> history,
                                   String systemPrompt,
                                   String userMessage,
                                   List<ChatTool> tools,
                                   UUID userId) {
        AiResponse response = openRouterClient.chatWithTools(history, systemPrompt, userMessage, tools);

        // Track full conversation để gửi tool result
        List<Map<String, Object>> conversation = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            conversation.add(Map.of("role", "system", "content", systemPrompt));
        }
        conversation.addAll(history);
        conversation.add(OpenRouterClient.buildContent("user", userMessage));

        int iteration = 0;
        while (response.isFunctionCall() && iteration < MAX_AGENTIC_ITERATIONS) {
            OpenRouterClient.FunctionCall fnCall = response.functionCall();
            String toolName = fnCall.name();
            Map<String, Object> args = fnCall.args();

            log.info("[AgenticLoop] Iteration {}: calling tool '{}' with args {}", iteration + 1, toolName, args);

            // Execute tool
            String toolResult = toolRegistry.findByName(toolName)
                    .map(tool -> {
                        try {
                            return tool.execute(args, userId);
                        } catch (Exception e) {
                            log.error("[AgenticLoop] Tool '{}' failed: {}", toolName, e.getMessage());
                            return "{\"error\": \"Không thể lấy dữ liệu, vui lòng thử lại\"}";
                        }
                    })
                    .orElse("{\"error\": \"Tool không tồn tại: " + toolName + "\"}");

            log.info("[AgenticLoop] Tool '{}' result: {}", toolName, toolResult);

            // Gửi kết quả tool về OpenRouter
            response = openRouterClient.sendToolResult(conversation, fnCall, toolResult);
            iteration++;
        }

        if (response.isFunctionCall()) {
            log.warn("[AgenticLoop] Max iterations reached, returning fallback message");
            return "Xin lỗi, tôi không thể hoàn thành yêu cầu này. Vui lòng thử lại sau.";
        }

        return response.text();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChatSession resolveUserSession(UUID userId, String sessionIdStr) {
        if (sessionIdStr != null && !sessionIdStr.isBlank()) {
            UUID sessionId;
            try {
                sessionId = UUID.fromString(sessionIdStr);
            } catch (IllegalArgumentException e) {
                throw new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND);
            }

            return sessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new ForbiddenException(ErrorCode.CHAT_SESSION_ACCESS_DENIED));
        }

        // Tạo session mới
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ChatSession session = ChatSession.builder()
                .user(user)
                .build();
        return sessionRepository.save(session);
    }

    private List<Map<String, Object>> buildHistory(UUID sessionId) {
        List<ChatMessage> messages = messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        // Đảo ngược để đưa về đúng thứ tự thời gian (từ cũ đến mới)
        Collections.reverse(messages);

        return messages.stream()
                .map(m -> OpenRouterClient.buildContent(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }

    private void saveMessage(ChatSession session, String role, String content) {
        ChatMessage msg = ChatMessage.builder()
                .session(session)
                .role(role)
                .content(content != null ? content : "")
                .build();
        messageRepository.save(msg);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
