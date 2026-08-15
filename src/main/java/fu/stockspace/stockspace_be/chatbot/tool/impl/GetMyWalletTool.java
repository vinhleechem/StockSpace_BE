package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wallet.dto.WalletResponse;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyWalletTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WalletService walletService;

    @Override
    public String getName() { return "getMyWallet"; }

    @Override
    public String getDescription() {
        return "Xem số dư ví của người thuê đang đăng nhập: số tiền khả dụng để thanh toán thuê kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem ví.\"}";
        }

        try {
            WalletResponse wallet = walletService.getWalletInfo(userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("balance", wallet.getBalance());
            result.put("currency", "VND");
            result.put("updatedAt", wallet.getUpdatedAt());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetMyWalletTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin ví lúc này.\"}";
        }
    }
}
