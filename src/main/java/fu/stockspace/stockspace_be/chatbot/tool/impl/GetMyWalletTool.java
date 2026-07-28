package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Tool: getMyWallet
 * Xem số dư ví của Tenant hiện tại.
 *
 * ⚠️ PENDING: Chờ Dev B expose WalletService.getBalance(UUID userId) → BigDecimal
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyWalletTool implements ChatTool {

    private final ObjectMapper objectMapper;

    // TODO: Inject WalletService khi Dev B expose getBalance()
    // private final WalletService walletService;

    @Override
    public String getName() { return "getMyWallet"; }

    @Override
    public String getDescription() {
        return "Xem số dư ví hiện tại của Tenant: số tiền khả dụng để thanh toán thuê kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            // TODO: Uncomment khi Dev B expose method:
            // BigDecimal balance = walletService.getBalance(userId);
            // return objectMapper.writeValueAsString(Map.of("balance", balance, "currency", "VND"));

            return objectMapper.writeValueAsString(Map.of(
                    "status", "pending_integration",
                    "message", "Chức năng đang được phát triển, vui lòng thử lại sau."
            ));

        } catch (Exception e) {
            log.error("[GetMyWalletTool] Error for userId {}: {}", userId, e.getMessage(), e);
            return "{\"error\": \"Không thể lấy thông tin ví lúc này.\"}";
        }
    }
}
