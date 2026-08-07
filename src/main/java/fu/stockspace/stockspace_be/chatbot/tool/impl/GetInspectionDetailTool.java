package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Tool: getInspectionDetail
 * Xem chi tiết biên bản / yêu cầu kiểm định kho bãi của Inspector.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetInspectionDetailTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final InspectionService inspectionService;

    @Override
    public String getName() {
        return "getInspectionDetail";
    }

    @Override
    public String getDescription() {
        return "Xem thông tin chi tiết một biên bản kiểm định kho bãi theo inspectionId (dành cho Inspector).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "inspectionId", Map.of(
                                "type", "string",
                                "description", "Mã UUID của yêu cầu/biên bản kiểm định (bắt buộc)"
                        )
                ),
                "required", java.util.List.of("inspectionId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Inspector để xem chi tiết kiểm định.\"}";
        }

        if (params == null || !params.containsKey("inspectionId") || params.get("inspectionId") == null) {
            return "{\"error\":\"Thiếu tham số inspectionId.\"}";
        }

        try {
            UUID inspectionId = UUID.fromString(params.get("inspectionId").toString());
            InspectionReportResponse response = inspectionService.getInspectionById(inspectionId);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {

            log.warn("[GetInspectionDetailTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin chi tiết kiểm định lúc này.\"}";
        }
    }
}
