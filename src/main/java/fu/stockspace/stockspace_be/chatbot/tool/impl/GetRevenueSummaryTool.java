package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Tool: getRevenueSummary
 * Xem thống kê tổng doanh thu theo từng tháng trong năm của Owner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetRevenueSummaryTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public String getName() {
        return "getRevenueSummary";
    }

    @Override
    public String getDescription() {
        return "Xem thống kê tổng doanh thu cho thuê kho theo từng tháng trong năm của Owner.";
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

            Optional<Wallet> walletOpt = walletRepository.findByUserId(userId);
            if (walletOpt.isEmpty()) {
                return "{\"year\":" + year + ",\"totalRevenue\":0,\"monthlyRevenue\":[]}";
            }

            Wallet wallet = walletOpt.get();
            List<TransactionType> revenueTypes = List.of(
                    TransactionType.DEPOSIT_RECEIVED,
                    TransactionType.DEPOSIT_PAYMENT
            );
            List<Object[]> monthlyData = transactionRepository.findMonthlyRevenueByWalletIdAndTypesAndYear(
                    wallet.getId(), revenueTypes, year);

            Map<Integer, BigDecimal> monthMap = new HashMap<>();
            BigDecimal totalRevenue = BigDecimal.ZERO;

            for (Object[] row : monthlyData) {
                if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                    int month = ((Number) row[0]).intValue();
                    BigDecimal amount = new BigDecimal(row[1].toString());
                    monthMap.merge(month, amount, BigDecimal::add);
                    totalRevenue = totalRevenue.add(amount);
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
            result.put("totalRevenue", totalRevenue);
            result.put("monthlyRevenue", monthlyList);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetRevenueSummaryTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thống kê doanh thu lúc này.\"}";
        }
    }
}
