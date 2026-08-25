package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetRevenueSummaryTool implements ChatTool {

    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getRevenueSummary";
    }

    @Override
    public String getDescription() {
        return "Xem trạng thái ghi nhận doanh thu thuê kho của Chủ kho. Tiền thuê được thanh toán ngoài StockSpace nên không được suy ra từ giao dịch ví.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "year", Map.of(
                                "type", "integer",
                                "description", "Năm cần thống kê doanh thu (mặc định là năm hiện tại)"
                        )
                )
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Owner để xem doanh thu.\"}";
        }

        try {
            int year = LocalDate.now().getYear();
            if (params != null && params.containsKey("year") && params.get("year") != null) {
                try {
                    year = Integer.parseInt(params.get("year").toString());
                } catch (NumberFormatException ignored) {}
            }

            List<Map<String, Object>> monthlyList = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                Map<String, Object> mItem = new LinkedHashMap<>();
                mItem.put("month", m);
                mItem.put("revenue", BigDecimal.ZERO);
                monthlyList.add(mItem);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("year", year);
            result.put("totalRevenue", BigDecimal.ZERO);
            result.put("monthlyRevenue", monthlyList);
            result.put("message", "StockSpace không thu hộ tiền thuê; doanh thu thực nhận không được ghi nhận trong ví nền tảng.");

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetRevenueSummaryTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thống kê doanh thu lúc này.\"}";
        }
    }
}
