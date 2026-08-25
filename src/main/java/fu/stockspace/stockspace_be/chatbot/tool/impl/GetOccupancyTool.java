package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.time.LocalDate;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetOccupancyTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseRepository warehouseRepository;
    private final RentalContractRepository contractRepository;

    @Override
    public String getName() {
        return "getOccupancyRate";
    }

    @Override
    public String getDescription() {
        return "Xem số hợp đồng, số người thuê đang hoạt động và tỷ lệ kho có người thuê của Chủ kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Owner để xem tỷ lệ lấp đầy kho.\"}";
        }

        try {
            List<Warehouse> warehouses = warehouseRepository.findByOwnerId(userId, Pageable.unpaged()).getContent();
            int total = warehouses.size();
            LocalDate today = LocalDate.now();
            Set<UUID> occupiedWarehouseIds = new HashSet<>(
                    contractRepository.findCurrentDirectActiveWarehouseIdsByOwnerId(userId, today));
            long activeContractCount = contractRepository
                    .countCurrentDirectActiveContractsByOwnerId(userId, today);
            long activeTenantCount = contractRepository
                    .countDistinctCurrentDirectActiveTenantsByOwnerId(userId, today);
            List<String> occupiedWarehouseNames = warehouses.stream()
                    .filter(warehouse -> occupiedWarehouseIds.contains(warehouse.getId()))
                    .map(Warehouse::getName)
                    .toList();

            double rate = total > 0 ? ((double) occupiedWarehouseIds.size() / total) * 100.0 : 0.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalWarehouses", total);
            result.put("warehousesWithActiveContracts", occupiedWarehouseIds.size());
            result.put("activeContractCount", activeContractCount);
            result.put("activeTenantCount", activeTenantCount);
            result.put("occupancyRatePercentage", Math.round(rate * 100.0) / 100.0);
            result.put("occupiedWarehouseNames", occupiedWarehouseNames);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetOccupancyTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy tỷ lệ lấp đầy kho lúc này.\"}";
        }
    }
}
