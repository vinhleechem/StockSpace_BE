package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRepository;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tool: getPlatformSummary
 * Xem thống kê tổng quan toàn nền tảng dành cho Admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetPlatformSummaryTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final BookingRepository bookingRepository;
    private final RentalContractRepository contractRepository;

    @Override
    public String getName() {
        return "getPlatformSummary";
    }

    @Override
    public String getDescription() {
        return "Xem tổng quan thống kê số lượng người dùng, kho bãi, cọc và hợp đồng trên toàn nền tảng (dành cho Admin).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Admin để xem thống kê nền tảng.\"}";
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalUsers", userRepository.count());
            result.put("totalWarehouses", warehouseRepository.count());
            result.put("totalBookings", bookingRepository.count());
            result.put("totalContracts", contractRepository.count());

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetPlatformSummaryTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thống kê nền tảng lúc này.\"}";
        }
    }
}
