package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public, read-only package catalogue. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetServicePackagesTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final ServicePackageService servicePackageService;

    @Override
    public String getName() {
        return "getServicePackages";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các gói dịch vụ đang cung cấp, gồm tên gói, quyền lợi, giá, "
                + "số nhân viên tối đa và thời hạn. Dùng cho câu hỏi về gói cơ bản, bảng giá hoặc so sánh gói.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            List<Map<String, Object>> packages = servicePackageService.getAllPackages().stream()
                    .map(this::toSafePackage)
                    .toList();
            return objectMapper.writeValueAsString(Map.of(
                    "packages", packages,
                    "total", packages.size()
            ));
        } catch (Exception exception) {
            log.warn("[GetServicePackagesTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách gói dịch vụ lúc này.\"}";
        }
    }

    private Map<String, Object> toSafePackage(ServicePackageResponse servicePackage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", servicePackage.getName());
        result.put("features", servicePackage.getFeatures());
        result.put("price", servicePackage.getPrice());
        result.put("durationDays", servicePackage.getDurationDays());
        result.put("maxStaff", servicePackage.getMaxStaff());
        return result;
    }
}
