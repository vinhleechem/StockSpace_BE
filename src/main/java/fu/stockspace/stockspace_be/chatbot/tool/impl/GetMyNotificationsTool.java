package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.notification.dto.NotificationResponse;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyNotificationsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    public String getName() {
        return "getMyNotifications";
    }

    @Override
    public String getDescription() {
        return "Xem thông báo gần đây và số thông báo chưa đọc của chính người thuê đang đăng nhập. Tool chỉ đọc, không đánh dấu đã đọc.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "page", Map.of("type", "integer", "minimum", 0),
                "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem thông báo.\"}";
        }
        try {
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            PagedResponse<NotificationResponse> page = notificationService.getMyNotifications(
                    userId, PageRequest.of(pageNumber, pageSize,
                            Sort.by(Sort.Direction.DESC, "createdAt")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("unreadCount", notificationService.countUnread(userId));
            result.put("notifications", page.getContent().stream().map(this::notification).toList());
            result.put("page", page.getPage());
            result.put("total", page.getTotalElements());
            result.put("totalPages", page.getTotalPages());
            result.put("hasMore", !page.isLast());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Thông tin phân trang không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[GetMyNotificationsTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông báo lúc này.\"}";
        }
    }

    private Map<String, Object> notification(NotificationResponse notification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", notification.getTitle());
        result.put("message", notification.getMessage());
        result.put("type", notification.getType());
        result.put("read", notification.isRead());
        result.put("createdAt", notification.getCreatedAt());
        return result;
    }
}
