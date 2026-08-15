package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.booking.dto.BookingResponse;
import fu.stockspace.stockspace_be.booking.service.BookingService;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetWarehouseBookingsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final BookingService bookingService;

    @Override
    public String getName() {
        return "getWarehouseBookings";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách các yêu cầu đặt cọc thuê kho gửi đến các kho của Chủ kho (Owner).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Owner để xem danh sách cọc kho.\"}";
        }

        try {
            PagedResponse<BookingResponse> paged = bookingService.getIncomingRequests(userId, 0, 50);
            return objectMapper.writeValueAsString(paged.getContent());
        } catch (Exception e) {
            log.warn("[GetWarehouseBookingsTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách yêu cầu cọc lúc này.\"}";
        }
    }
}
