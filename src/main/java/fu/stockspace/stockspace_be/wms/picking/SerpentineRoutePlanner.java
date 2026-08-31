package fu.stockspace.stockspace_be.wms.picking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic SERPENTINE_XY_V1 route planner.
 *
 * <p>This is a layout-grid heuristic, not a shortest-path algorithm. It only
 * orders the already selected FIFO allocations and never changes their batch
 * or quantity.</p>
 */
@Component
public class SerpentineRoutePlanner {

    private static final String RACK_COORDINATE_WARNING =
            "Rack is missing coordinateX or coordinateY; rackCode and rackId fallback is used";
    private static final String BIN_COORDINATE_WARNING =
            "Bin is missing coordinateX; binCode and binId fallback is used";

    public PickRoutePlan plan(List<PickRouteCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        candidates.forEach(this::validateCandidate);

        Map<UUID, List<PickRouteCandidate>> candidatesByRack = candidates.stream()
                .collect(Collectors.groupingBy(PickRouteCandidate::rackId,
                        LinkedHashMap::new, Collectors.toList()));
        List<RackGroup> sortedRacks = candidatesByRack.entrySet().stream()
                .map(entry -> newRackGroup(entry.getKey(), entry.getValue()))
                .sorted(this::compareByYThenFallback)
                .toList();

        List<List<RackGroup>> rows = groupByY(sortedRacks);
        List<PickRouteStop> stops = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<UUID> warnedRacks = new HashSet<>();
        Set<UUID> warnedBins = new HashSet<>();
        int sequence = 1;

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            boolean ascending = rowIndex % 2 == 0;
            List<RackGroup> row = new ArrayList<>(rows.get(rowIndex));
            row.sort((left, right) -> compareRack(left, right, ascending));

            for (RackGroup rack : row) {
                if ((rack.coordinateX() == null || rack.coordinateY() == null)
                        && warnedRacks.add(rack.rackId())) {
                    warnings.add(rackWarning(rack));
                }

                Map<UUID, List<PickRouteCandidate>> candidatesByBin = rack.candidates().stream()
                        .collect(Collectors.groupingBy(PickRouteCandidate::binId,
                                LinkedHashMap::new, Collectors.toList()));
                List<BinGroup> bins = candidatesByBin.entrySet().stream()
                        .map(entry -> newBinGroup(entry.getKey(), entry.getValue()))
                        .sorted((left, right) -> compareBin(left, right, ascending))
                        .toList();

                for (BinGroup bin : bins) {
                    if (bin.coordinateX() == null && warnedBins.add(bin.binId())) {
                        warnings.add(binWarning(rack, bin));
                    }
                    List<PickRouteAllocation> allocations = bin.candidates().stream()
                            .map(candidate -> new PickRouteAllocation(
                                    candidate.skuId(), candidate.stockBatchId(), candidate.quantity()))
                            .toList();
                    stops.add(new PickRouteStop(
                            sequence++,
                            rack.rackId(),
                            rack.rackCode(),
                            bin.binId(),
                            bin.binCode(),
                            allocations));
                }
            }
        }

        return new PickRoutePlan(List.copyOf(stops), List.copyOf(warnings));
    }

    private List<List<RackGroup>> groupByY(List<RackGroup> sortedRacks) {
        List<List<RackGroup>> rows = new ArrayList<>();
        for (RackGroup rack : sortedRacks) {
            if (rows.isEmpty()
                    || compareCoordinate(rack.coordinateY(), rows.get(rows.size() - 1).get(0).coordinateY()) != 0) {
                rows.add(new ArrayList<>());
            }
            rows.get(rows.size() - 1).add(rack);
        }
        return rows;
    }

    private int compareByYThenFallback(RackGroup left, RackGroup right) {
        int coordinateComparison = compareCoordinate(left.coordinateY(), right.coordinateY());
        if (coordinateComparison != 0) {
            return coordinateComparison;
        }
        return compareRackFallback(left.rackCode(), left.rackId(), right.rackCode(), right.rackId());
    }

    private int compareRack(RackGroup left, RackGroup right, boolean ascending) {
        int coordinateComparison = compareCoordinate(left.coordinateX(), right.coordinateX(), ascending);
        if (coordinateComparison != 0) {
            return coordinateComparison;
        }
        return compareRackFallback(left.rackCode(), left.rackId(), right.rackCode(), right.rackId());
    }

    private int compareBin(BinGroup left, BinGroup right, boolean ascending) {
        int coordinateComparison = compareCoordinate(left.coordinateX(), right.coordinateX(), ascending);
        if (coordinateComparison != 0) {
            return coordinateComparison;
        }
        int shelfComparison = Comparator.nullsLast(Integer::compareTo)
                .compare(left.shelfLevel(), right.shelfLevel());
        if (shelfComparison != 0) {
            return shelfComparison;
        }
        int codeComparison = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                .compare(left.binCode(), right.binCode());
        if (codeComparison != 0) {
            return codeComparison;
        }
        return left.binId().compareTo(right.binId());
    }

    private int compareCoordinate(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private int compareCoordinate(BigDecimal left, BigDecimal right, boolean ascending) {
        int comparison = compareCoordinate(left, right);
        if (comparison == 0 || left == null || right == null) {
            return comparison;
        }
        return ascending ? comparison : -comparison;
    }

    private int compareRackFallback(String leftCode, UUID leftId,
                                    String rightCode, UUID rightId) {
        int codeComparison = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                .compare(leftCode, rightCode);
        return codeComparison != 0 ? codeComparison : leftId.compareTo(rightId);
    }

    private RackGroup newRackGroup(UUID rackId, List<PickRouteCandidate> candidates) {
        PickRouteCandidate first = candidates.get(0);
        return new RackGroup(rackId, first.rackCode(), first.rackCoordinateX(),
                first.rackCoordinateY(), candidates);
    }

    private BinGroup newBinGroup(UUID binId, List<PickRouteCandidate> candidates) {
        PickRouteCandidate first = candidates.get(0);
        return new BinGroup(binId, first.binCode(), first.binCoordinateX(),
                first.shelfLevel(), candidates);
    }

    private void validateCandidate(PickRouteCandidate candidate) {
        if (candidate == null || candidate.skuId() == null || candidate.stockBatchId() == null
                || candidate.rackId() == null || candidate.binId() == null
                || candidate.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Each pick route candidate must contain SKU, batch, rack, bin and positive quantity");
        }
    }

    private String rackWarning(RackGroup rack) {
        return RACK_COORDINATE_WARNING + " for " + locationLabel(rack.rackCode(), rack.rackId());
    }

    private String binWarning(RackGroup rack, BinGroup bin) {
        return BIN_COORDINATE_WARNING + " for "
                + locationLabel(rack.rackCode(), rack.rackId()) + "/"
                + locationLabel(bin.binCode(), bin.binId());
    }

    private String locationLabel(String code, UUID id) {
        return code == null || code.isBlank() ? id.toString() : code;
    }

    private record RackGroup(
            UUID rackId,
            String rackCode,
            BigDecimal coordinateX,
            BigDecimal coordinateY,
            List<PickRouteCandidate> candidates
    ) {
    }

    private record BinGroup(
            UUID binId,
            String binCode,
            BigDecimal coordinateX,
            Integer shelfLevel,
            List<PickRouteCandidate> candidates
    ) {
    }
}
