package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owner-only read tool. The selected warehouse id is supplied by the
 * application context and is never part of the model function schema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCurrentWarehouseStaffTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseRepository warehouseRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;

    @Override
    public String getName() {
        return "getCurrentWarehouseStaff";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách nhân viên đang được phân công tại kho hiện tại của Chủ kho. "
                + "Chỉ dùng ngữ cảnh kho đang được chọn, không cần mã kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return missingWarehouseContext();
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        if (context == null || context.userId() == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Chủ kho để xem nhân viên.\"}";
        }
        if (context.activeWarehouseId() == null) {
            return missingWarehouseContext();
        }

        try {
            Warehouse warehouse = warehouseRepository
                    .findByIdAndOwnerId(context.activeWarehouseId(), context.userId())
                    .orElse(null);
            if (warehouse == null) {
                return "{\"error\":\"Bạn không có quyền xem nhân viên của kho đang chọn.\"}";
            }

            List<Map<String, Object>> staff = assignmentRepository
                    .findActiveByWarehouseId(warehouse.getId())
                    .stream()
                    .map(this::toSafeStaff)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", warehouse.getName());
            result.put("staffCount", staff.size());
            result.put("staff", staff);
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            log.warn("[GetCurrentWarehouseStaffTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách nhân viên lúc này.\"}";
        }
    }

    private Map<String, Object> toSafeStaff(StaffWarehouseAssignment assignment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fullName", assignment.getStaff().getFullName());
        result.put("jobTitle", assignment.getCustomTitle());
        result.put("assignedSince", assignment.getStartDate() == null
                ? null
                : assignment.getStartDate().toString());
        return result;
    }

    private String missingWarehouseContext() {
        return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
    }
}
