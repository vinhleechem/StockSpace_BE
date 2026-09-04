package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.common.service.SystemPolicyService;
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
public class GetCurrentSystemRulesTool implements ChatTool {

    private static final int MAX_POLICY_CONTENT_CHARS = 12_000;

    private final ObjectMapper objectMapper;
    private final SystemPolicyService policyService;
    private final SystemConfigService configService;

    @Override
    public String getName() {
        return "getCurrentSystemRules";
    }

    @Override
    public String getDescription() {
        return "Đọc chính sách đang có hiệu lực và các cấu hình công khai hiện tại của StockSpace, "
                + "bao gồm hạn xác nhận hợp đồng và phí kiểm định. Dùng nguồn này cho số liệu động thay vì tài liệu hướng dẫn cũ.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            try {
                SystemPolicyResponse policy = policyService.getActivePolicy();
                Map<String, Object> activePolicy = new LinkedHashMap<>();
                activePolicy.put("version", policy.getVersion());
                String content = policy.getContent();
                boolean truncated = content != null && content.length() > MAX_POLICY_CONTENT_CHARS;
                activePolicy.put("content", truncated
                        ? content.substring(0, MAX_POLICY_CONTENT_CHARS)
                        : content);
                activePolicy.put("contentTruncated", truncated);
                activePolicy.put("effectiveFrom", policy.getCreatedAt());
                activePolicy.put("updatedAt", policy.getUpdatedAt());
                result.put("activePolicy", activePolicy);
            } catch (ResourceNotFoundException exception) {
                result.put("activePolicy", null);
                result.put("policyMessage", "Hiện chưa có chính sách hệ thống đang hiệu lực.");
            }

            List<Map<String, Object>> configs = configService.getPublicConfigs().stream()
                    .map(this::toConfig)
                    .toList();
            result.put("publicConfigs", configs);
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            log.warn("[GetCurrentSystemRulesTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy chính sách và cấu hình hiện tại lúc này.\"}";
        }
    }

    private Map<String, Object> toConfig(SystemConfigResponse config) {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("contract_expiry_days".equalsIgnoreCase(config.getConfigKey())) {
            result.put("name", "Thời hạn tối đa để người thuê xác nhận hợp đồng");
            result.put("unit", "ngày");
        } else if ("inspection_fee".equalsIgnoreCase(config.getConfigKey())) {
            result.put("name", "Phí gửi yêu cầu kiểm định kho");
            result.put("unit", "VND");
        } else {
            result.put("name", "Cấu hình công khai");
        }
        result.put("value", config.getConfigValue());
        result.put("description", config.getDescription());
        result.put("updatedAt", config.getUpdatedAt());
        return result;
    }
}
