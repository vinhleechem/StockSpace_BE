package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Read-only tenant tool for questions about the package currently in use. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyActiveSubscriptionTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;

    @Override
    public String getName() {
        return "getMyActiveSubscription";
    }

    @Override
    public String getDescription() {
        return "Xem gói dịch vụ đang sử dụng của người thuê đã đăng nhập, gồm tên gói, quyền lợi, giá, "
                + "số nhân viên tối đa và thời hạn. Dùng khi người dùng hỏi gói của tôi, gói đang dùng, "
                + "gói cơ bản của tôi hoặc ngày hết hạn gói; không dùng cho hợp đồng thuê kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem gói dịch vụ đang dùng.\"}";
        }

        try {
            SubscriptionResponse subscription = subscriptionService.getMyActiveSubscription(userId);
            ServicePackageResponse servicePackage = subscription.getServicePackage();
            if (servicePackage == null) {
                return "{\"error\":\"Không tìm thấy thông tin gói dịch vụ đang dùng.\"}";
            }

            Map<String, Object> packageInfo = new LinkedHashMap<>();
            packageInfo.put("name", servicePackage.getName());
            packageInfo.put("features", servicePackage.getFeatures());
            packageInfo.put("price", servicePackage.getPrice());
            packageInfo.put("durationDays", servicePackage.getDurationDays());
            packageInfo.put("maxStaff", servicePackage.getMaxStaff());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("servicePackage", packageInfo);
            result.put("startDate", subscription.getStartDate());
            result.put("endDate", subscription.getEndDate());
            result.put("status", "Đang hiệu lực");
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException exception) {
            return "{\"message\":\"Bạn hiện chưa có gói dịch vụ nào đang hiệu lực.\"}";
        } catch (Exception exception) {
            log.warn("[GetMyActiveSubscriptionTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin gói dịch vụ lúc này.\"}";
        }
    }
}
