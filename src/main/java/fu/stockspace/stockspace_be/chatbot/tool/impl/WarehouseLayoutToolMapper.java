package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.warehouse.dto.RackResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseBinResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WarehouseLayoutToolMapper {

    private static final int MAX_RACKS = 12;
    private static final int MAX_BINS_PER_RACK = 10;

    private WarehouseLayoutToolMapper() {
    }

    static Map<String, Object> toSafeMap(WarehouseLayoutResponse layout) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("widthMeters", layout.getWidth());
        result.put("lengthMeters", layout.getLength());
        result.put("heightMeters", layout.getHeight());
        result.put("totalRacks", layout.getTotalRacks());
        result.put("totalBins", layout.getTotalBins());
        result.put("occupiedBins", layout.getOccupiedBins());
        result.put("emptyBins", layout.getEmptyBins());
        List<RackResponse> racks = layout.getRacks() == null ? List.of() : layout.getRacks();
        result.put("racks", racks.stream().limit(MAX_RACKS).map(WarehouseLayoutToolMapper::rack).toList());
        result.put("racksReturned", Math.min(racks.size(), MAX_RACKS));
        result.put("racksTruncated", racks.size() > MAX_RACKS);
        return result;
    }

    private static Map<String, Object> rack(RackResponse rack) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", rack.getName());
        result.put("code", rack.getCode());
        result.put("maxWeightKg", rack.getMaxWeight());
        result.put("maxVolumeM3", rack.getMaxVolume());
        result.put("shelfCount", rack.getShelfCount());
        List<WarehouseBinResponse> bins = rack.getBins() == null ? List.of() : rack.getBins();
        result.put("bins", bins.stream().limit(MAX_BINS_PER_RACK)
                .map(WarehouseLayoutToolMapper::bin).toList());
        result.put("binsTruncated", bins.size() > MAX_BINS_PER_RACK);
        return result;
    }

    private static Map<String, Object> bin(WarehouseBinResponse bin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", bin.getName());
        result.put("code", bin.getCode());
        result.put("shelfLevel", bin.getShelfLevel());
        result.put("maxWeightKg", bin.getMaxWeight());
        result.put("maxVolumeM3", bin.getMaxVolume());
        result.put("occupied", bin.isOccupied());
        return result;
    }
}
