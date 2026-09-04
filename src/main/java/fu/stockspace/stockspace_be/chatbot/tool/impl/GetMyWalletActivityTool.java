package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wallet.dto.TransactionResponse;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawResponse;
import fu.stockspace.stockspace_be.wallet.service.TransactionService;
import fu.stockspace.stockspace_be.wallet.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyWalletActivityTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;
    private final WithdrawService withdrawService;

    @Override
    public String getName() {
        return "getMyWalletActivity";
    }

    @Override
    public String getDescription() {
        return "Xem lịch sử giao dịch, trạng thái một mã thanh toán hoặc lịch sử yêu cầu rút tiền của người thuê. "
                + "Không trả số tài khoản ngân hàng và không tạo giao dịch mới.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "view", Map.of("type", "string", "enum", List.of("TRANSACTIONS", "WITHDRAWALS")),
                        "paymentCode", Map.of("type", "string", "description", "Mã thanh toán nếu cần kiểm tra một giao dịch"),
                        "page", Map.of("type", "integer", "minimum", 0),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem hoạt động ví.\"}";
        }
        try {
            Object rawCode = params == null ? null : params.get("paymentCode");
            if (rawCode != null && !rawCode.toString().isBlank()) {
                TransactionResponse transaction = transactionService.getTransactionStatus(
                        userId, rawCode.toString().trim());
                return objectMapper.writeValueAsString(Map.of("transaction", transaction(transaction)));
            }

            String view = params == null || params.get("view") == null
                    ? "TRANSACTIONS" : params.get("view").toString().trim().toUpperCase(Locale.ROOT);
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            PageRequest pageable = PageRequest.of(pageNumber, pageSize,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            return switch (view) {
                case "TRANSACTIONS" -> readTransactions(userId, pageable);
                case "WITHDRAWALS" -> readWithdrawals(userId, pageable);
                default -> throw new IllegalArgumentException("Unsupported wallet view");
            };
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Tham số lịch sử ví không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[GetMyWalletActivityTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy hoạt động ví lúc này.\"}";
        }
    }

    private String readTransactions(UUID userId, PageRequest pageable) throws Exception {
        PagedResponse<TransactionResponse> page = transactionService.getMyTransactions(userId, pageable);
        Map<String, Object> result = metadata(page.getPage(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
        result.put("transactions", page.getContent().stream().map(this::transaction).toList());
        return objectMapper.writeValueAsString(result);
    }

    private String readWithdrawals(UUID userId, PageRequest pageable) throws Exception {
        Page<WithdrawResponse> page = withdrawService.getMyWithdrawRequests(userId, pageable);
        Map<String, Object> result = metadata(page.getNumber(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
        result.put("withdrawals", page.getContent().stream().map(this::withdrawal).toList());
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> metadata(int page, long total, int totalPages, boolean last) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("hasMore", !last);
        return result;
    }

    private Map<String, Object> transaction(TransactionResponse transaction) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", transaction.getAmount());
        result.put("type", transaction.getTransactionType());
        result.put("paymentMethod", transaction.getPaymentMethod());
        result.put("status", transaction.getStatus());
        result.put("paymentCode", transaction.getPaymentCode());
        result.put("createdAt", transaction.getCreatedAt());
        return result;
    }

    private Map<String, Object> withdrawal(WithdrawResponse withdrawal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", withdrawal.getAmount());
        result.put("bankName", withdrawal.getBankName());
        result.put("status", withdrawal.getStatus());
        result.put("adminNotes", withdrawal.getAdminNotes());
        result.put("createdAt", withdrawal.getCreatedAt());
        result.put("updatedAt", withdrawal.getUpdatedAt());
        return result;
    }
}
