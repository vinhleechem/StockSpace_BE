package fu.stockspace.stockspace_be.wms.dataexchange.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.wms.dataexchange.job.CanonicalContentHashService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StockFingerprintService {

    private final StockBatchRepository stockBatchRepository;
    private final StockTransferReservationRepository reservationRepository;
    private final CanonicalContentHashService hashService;
    private final ObjectMapper objectMapper;

    /**
     * Fingerprint is a change detector for offline workbooks, not an
     * authorization mechanism or a database signature.
     */
    public String fingerprint(UUID warehouseId) {
        List<Map<String, Object>> batches = stockBatchRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId).stream()
                .filter(batch -> batch.isActive() && !batch.isDeleted())
                .sorted(Comparator.comparing(StockBatch::getId))
                .map(this::batch)
                .toList();
        List<Map<String, Object>> reservations = reservationRepository.findActiveForFingerprint(warehouseId).stream()
                .sorted(Comparator.comparing(StockTransferReservationRepository.ReservationFingerprintProjection::getReservationId))
                .map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("reservation_id", item.getReservationId());
                    value.put("batch_id", item.getBatchId());
                    value.put("quantity", item.getQuantity());
                    value.put("status", item.getStatus());
                    value.put("updated_at", item.getUpdatedAt());
                    return value;
                }).toList();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("warehouse_id", warehouseId);
        root.put("batches", batches);
        root.put("reservations", reservations);
        try {
            return hashService.sha256(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize stock fingerprint", ex);
        }
    }

    private Map<String, Object> batch(StockBatch item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("batch_id", item.getId());
        value.put("sku_id", item.getSkuId());
        value.put("rack_id", item.getRack() == null ? null : item.getRack().getId());
        value.put("bin_id", item.getBin() == null ? null : item.getBin().getId());
        value.put("quantity", item.getQuantity());
        value.put("arrival_date", item.getArrivalDate());
        value.put("updated_at", item.getUpdatedAt());
        return value;
    }
}
