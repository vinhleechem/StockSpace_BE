package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreviewSubscriptionChangeTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final ServicePackageService packageService;
    private final SubscriptionService subscriptionService;

    @Override
    public String getName() {
        return "previewSubscriptionChange";
    }

    @Override
    public String getDescription() {
        return "Kiểm tra trước việc mua, gia hạn, nâng cấp hoặc hạ gói theo tên gói đang được cung cấp. "
                + "Tool chỉ tính và giải thích, không trừ tiền hay thay đổi gói.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("packageName", Map.of(
                        "type", "string", "description", "Tên chính xác của gói dịch vụ")),
                "required", List.of("packageName"));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem trước thay đổi gói.\"}";
        }
        try {
            Object rawName = params == null ? null : params.get("packageName");
            if (rawName == null || rawName.toString().isBlank()) {
                throw new IllegalArgumentException("packageName is required");
            }
            String requestedName = rawName.toString().trim();
            ServicePackageResponse target = packageService.getAllPackages().stream()
                    .filter(candidate -> candidate.getName() != null
                            && candidate.getName().equalsIgnoreCase(requestedName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Package name was not found"));
            SubscriptionPreviewResponse preview = subscriptionService.previewSubscriptionChange(
                    userId, target.getId());
            return objectMapper.writeValueAsString(toMap(preview));
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Không tìm thấy gói dịch vụ đang cung cấp với tên đã nhập. Hãy xem danh sách gói trước.\"}";
        } catch (Exception exception) {
            log.warn("[PreviewSubscriptionChangeTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể xem trước thay đổi gói lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(SubscriptionPreviewResponse preview) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentPackageName", preview.getCurrentPackageName());
        result.put("currentMaxStaff", preview.getCurrentMaxStaff());
        result.put("currentPrice", preview.getCurrentPrice());
        result.put("newPackageName", preview.getNewPackageName());
        result.put("newMaxStaff", preview.getNewMaxStaff());
        result.put("newPrice", preview.getNewPrice());
        result.put("changeType", preview.getTransactionType());
        result.put("canProceed", preview.isCanProceed());
        result.put("message", preview.getMessage());
        return result;
    }
}
