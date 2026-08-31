package fu.stockspace.stockspace_be.wms.picking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic, side-effect-free FIFO planner for one SKU.
 *
 * <p>Candidates are expected to be pre-filtered to the requested SKU and
 * warehouse by the calling service. Inactive, deleted, empty and malformed
 * candidates are ignored here as a final safety guard.</p>
 */
@Component
public class FifoAllocationPlanner {

    public FifoAllocationPlan plan(List<FifoCandidate> candidates, int requestedQuantity) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be greater than 0");
        }

        List<FifoCandidate> eligibleCandidates = candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.stockBatchId() != null)
                .filter(candidate -> candidate.active() && !candidate.deleted())
                .filter(candidate -> candidate.quantity() > 0)
                .sorted(fifoComparator())
                .toList();

        List<FifoAllocation> allocations = new ArrayList<>();
        int remainingQuantity = requestedQuantity;
        for (int index = 0; index < eligibleCandidates.size() && remainingQuantity > 0; index++) {
            FifoCandidate candidate = eligibleCandidates.get(index);
            int allocatedQuantity = Math.min(remainingQuantity, candidate.quantity());
            allocations.add(new FifoAllocation(
                    candidate.stockBatchId(),
                    allocatedQuantity,
                    candidate.arrivalDate(),
                    reasons(candidate, eligibleCandidates, index)));
            remainingQuantity -= allocatedQuantity;
        }

        return new FifoAllocationPlan(List.copyOf(allocations), remainingQuantity);
    }

    private Comparator<FifoCandidate> fifoComparator() {
        return Comparator.comparing(FifoCandidate::arrivalDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FifoCandidate::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FifoCandidate::stockBatchId, UUID::compareTo);
    }

    private List<String> reasons(FifoCandidate candidate,
                                 List<FifoCandidate> sortedCandidates,
                                 int index) {
        List<String> reasons = new ArrayList<>();
        if (candidate.arrivalDate() == null) {
            reasons.add("Arrival date is missing; fallback ordering is applied");
        } else {
            reasons.add("Earliest available arrival date");
        }

        if (index > 0) {
            FifoCandidate previous = sortedCandidates.get(index - 1);
            if (Objects.equals(candidate.arrivalDate(), previous.arrivalDate())) {
                if (Objects.equals(candidate.createdAt(), previous.createdAt())) {
                    reasons.add("Batch ID tie-break");
                } else {
                    reasons.add("Created-at tie-break");
                }
            }
        }
        reasons.add("Selected in FIFO order");
        return List.copyOf(reasons);
    }
}
