package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wms.capacity.dto.RackCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.WarehouseLayoutCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.service.WarehouseCapacityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetWarehouseCapacityTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseCapacityService capacityService;

    @Override
    public String getName() {
        return "getWarehouseCapacity";
    }

    @Override
    public String getDescription() {
        return "Xem tải trọng và thể tích đang sử dụng của các kệ, ô chứa tại kho đang được chọn, "
                + "bao gồm tỷ lệ sử dụng và cảnh báo đầy hoặc vượt sức chứa.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "warehouseId", Map.of("type", "string",
                        "description", "UUID kho cần xem sức chứa. Bỏ trống để dùng kho đang mở trên giao diện.")));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return read(userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        UUID userId = context == null ? null : context.userId();
        // AI-supplied warehouseId takes priority over the page-context warehouse
        UUID explicitId = warehouseIdFromParams(params);
        UUID warehouseId = explicitId != null ? explicitId
                : (context == null ? null : context.activeWarehouseId());
        return read(userId, warehouseId);
    }

    private String read(UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem sức chứa kho.\"}";
        }
        if (warehouseId == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }
        try {
            WarehouseLayoutCapacityResponse capacity = capacityService.getCapacity(userId, warehouseId, null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", capacity.getWarehouseName());
            result.put("rackCount", capacity.getRacks().size());
            result.put("racks", capacity.getRacks().stream().limit(20).map(this::toRack).toList());
            result.put("racksReturned", Math.min(capacity.getRacks().size(), 20));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetWarehouseCapacityTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy dữ liệu sức chứa kho lúc này.\"}";
        }
    }

    private Map<String, Object> toRack(RackCapacityResponse rack) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rack", rack.getRackName());
        result.put("status", ChatToolLocalization.capacityStatus(rack.getCapacityStatus()));
        result.put("currentWeightKg", rack.getCurrentWeightKg());
        result.put("maxWeightKg", rack.getMaxWeightKg());
        result.put("remainingWeightKg", rack.getRemainingWeightKg());
        result.put("weightUtilizationPercent", rack.getWeightUtilizationPercent());
        result.put("currentVolumeM3", rack.getCurrentVolumeM3());
        result.put("maxVolumeM3", rack.getMaxVolumeM3());
        result.put("remainingVolumeM3", rack.getRemainingVolumeM3());
        result.put("volumeUtilizationPercent", rack.getVolumeUtilizationPercent());
        result.put("binCount", rack.getBins() == null ? 0 : rack.getBins().size());
        result.put("fullOrOverCapacityBins", rack.getBins() == null ? 0 : rack.getBins().stream()
                .filter(bin -> bin.getCapacityStatus() != null
                        && (bin.getCapacityStatus().name().equals("FULL")
                        || bin.getCapacityStatus().name().equals("OVER_CAPACITY")))
                .count());
        return result;
    }

    private UUID warehouseIdFromParams(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("warehouseId");
        try {
            return raw == null ? null : UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
