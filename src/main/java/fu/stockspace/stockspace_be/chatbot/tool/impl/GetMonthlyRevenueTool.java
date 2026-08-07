package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Tool: getMonthlyRevenue
 * Xem doanh thu phí hoa hồng toàn nền tảng theo từng tháng cho Admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMonthlyRevenueTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "getMonthlyRevenue";
    }

    @Override
    public String getDescription() {
        return "Xem thống kê doanh thu phí hoa hồng của hệ thống theo từng tháng trong năm (dành cho Admin).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "year", Map.of(
                                "type", "integer",
                                "description", "Năm cần thống kê doanh thu hoa hồng (mặc định năm hiện tại)"
                        )
                )
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Admin để xem doanh thu hoa hồng.\"}";
        }

        try {
            int year = LocalDate.now().getYear();
            if (params != null && params.containsKey("year") && params.get("year") != null) {
                try {
                    year = Integer.parseInt(params.get("year").toString());
                } catch (NumberFormatException ignored) {}
            }

            List<Object[]> monthlyData = transactionRepository.findMonthlyRevenueByTypeAndYear(
                    TransactionType.COMMISSION, year);

            Map<Integer, BigDecimal> monthMap = new HashMap<>();
            BigDecimal totalCommission = BigDecimal.ZERO;

            for (Object[] row : monthlyData) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    int month = ((Number) row[0]).intValue();
                    BigDecimal amount = new BigDecimal(row[1].toString());
                    monthMap.put(month, amount);
                    totalCommission = totalCommission.add(amount);
                }
            }

            List<Map<String, Object>> monthlyList = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                Map<String, Object> mItem = new LinkedHashMap<>();
                mItem.put("month", m);
                mItem.put("revenue", monthMap.getOrDefault(m, BigDecimal.ZERO));
                monthlyList.add(mItem);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("year", year);
            result.put("totalCommission", totalCommission);
            result.put("monthlyCommission", monthlyList);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetMonthlyRevenueTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thống kê doanh thu hoa hồng lúc này.\"}";
        }
    }
}
