package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyInspectionsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final InspectionService inspectionService;

    @Override
    public String getName() {
        return "getMyAssignedInspections";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách các yêu cầu kiểm định kho bãi được phân công cho Thanh tra viên (Inspector).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Inspector để xem nhiệm vụ kiểm định.\"}";
        }

        try {
            org.springframework.data.domain.Page<InspectionReportResponse> page = inspectionService.getAssignedInspections(userId, 0, 50);
            return objectMapper.writeValueAsString(page.getContent());
        } catch (Exception e) {

            log.warn("[GetMyInspectionsTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách nhiệm vụ kiểm định lúc này.\"}";
        }
    }
}
