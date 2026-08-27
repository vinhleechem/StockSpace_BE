package fu.stockspace.stockspace_be.wms.putaway;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic, side-effect-free planner used after physical capacity has
 * been calculated for every candidate bin.
 */
public class PutawaySuggestionPlanner {

    private static final long MAX_SCORE = 1_000L;

    public PutawayPlan plan(List<PutawayCandidate> candidates,
                            int requestedQuantity,
                            boolean heavyItem) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be greater than 0");
        }

        List<WorkingCandidate> remainingCandidates = candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.maxQuantity() > 0)
                .map(candidate -> new WorkingCandidate(candidate, candidate.maxQuantity()))
                .toList();
        List<PutawayAllocation> allocations = new ArrayList<>();
        int remainingQuantity = requestedQuantity;

        while (remainingQuantity > 0 && !remainingCandidates.isEmpty()) {
            List<WorkingCandidate> ranked = remainingCandidates.stream()
                    .sorted(comparator(remainingQuantity, heavyItem))
                    .toList();
            WorkingCandidate selected = ranked.get(0);
            int allocatedQuantity = Math.min(remainingQuantity, selected.maxQuantity());
            int rank = ranked.indexOf(selected);
            allocations.add(new PutawayAllocation(
                    selected.candidate().rackId(),
                    selected.candidate().binId(),
                    allocatedQuantity,
                    Math.max(1L, MAX_SCORE - rank),
                    reasons(selected.candidate(), allocatedQuantity, remainingQuantity, heavyItem)));

            remainingCandidates = remainingCandidates.stream()
                    .map(candidate -> candidate == selected
                            ? new WorkingCandidate(candidate.candidate(), candidate.maxQuantity() - allocatedQuantity)
                            : candidate)
                    .filter(candidate -> candidate.maxQuantity() > 0)
                    .toList();
            remainingQuantity -= allocatedQuantity;
        }

        return new PutawayPlan(List.copyOf(allocations), remainingQuantity);
    }

    private Comparator<WorkingCandidate> comparator(int remainingQuantity, boolean heavyItem) {
        Comparator<WorkingCandidate> comparator = Comparator
                .comparing((WorkingCandidate candidate) -> candidate.candidate().containsSku())
                .reversed()
                .thenComparing(candidate -> candidate.maxQuantity() >= remainingQuantity ? 0 : 1)
                .thenComparing(candidate -> candidate.candidate().remainingCapacityRatio(),
                        Comparator.nullsLast(Comparator.naturalOrder()));

        if (heavyItem) {
            comparator = comparator.thenComparing(candidate -> valueOrZero(candidate.candidate().positionZ()));
        }

        return comparator
                .thenComparing(candidate -> candidate.candidate().rackCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(candidate -> candidate.candidate().binCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(candidate -> candidate.candidate().rackId(), Comparator.nullsLast(UUID::compareTo))
                .thenComparing(candidate -> candidate.candidate().binId(), Comparator.nullsLast(UUID::compareTo));
    }

    private List<String> reasons(PutawayCandidate candidate,
                                 int allocatedQuantity,
                                 int remainingQuantity,
                                 boolean heavyItem) {
        List<String> reasons = new ArrayList<>();
        if (candidate.containsSku()) {
            reasons.add("Bin already contains the same SKU");
        }
        if (allocatedQuantity == remainingQuantity) {
            reasons.add("Bin can hold the remaining quantity");
        } else {
            reasons.add("Partial allocation because the remaining capacity is limited");
        }
        if (candidate.remainingCapacityRatio() != null) {
            reasons.add("Smallest remaining capacity among suitable locations");
        }
        if (heavyItem) {
            reasons.add("Lower position is preferred for a weighted SKU");
        }
        reasons.add("Stable rack and bin code tie-break");
        return List.copyOf(reasons);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record WorkingCandidate(PutawayCandidate candidate, int maxQuantity) {
    }
}
