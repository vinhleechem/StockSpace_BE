package fu.stockspace.stockspace_be.wms.putaway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PutawaySuggestionPlannerTest {

    private static final UUID RACK_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RACK_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BIN_A = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BIN_B = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private final PutawaySuggestionPlanner planner = new PutawaySuggestionPlanner();

    @Test
    void sameSkuBin_isPreferredOverEmptyBin() {
        PutawayPlan plan = planner.plan(List.of(
                candidate(RACK_B, BIN_B, "RACK-B", "BIN-B", 1, true, 10, "0.80"),
                candidate(RACK_A, BIN_A, "RACK-A", "BIN-A", 1, false, 10, "0.10")
        ), 5, false);

        assertEquals(BIN_B, plan.allocations().get(0).binId());
        assertEquals(5, plan.allocations().get(0).quantity());
        assertEquals(0, plan.unallocatedQuantity());
        assertTrue(plan.allocations().get(0).reasons().get(0).contains("same SKU"));
    }

    @Test
    void heavyItem_prefersLowerPositionWhenHigherRulesTie() {
        PutawayPlan plan = planner.plan(List.of(
                candidate(RACK_B, BIN_B, "RACK-B", "BIN-B", 5, false, 10, "0.20"),
                candidate(RACK_A, BIN_A, "RACK-A", "BIN-A", 1, false, 10, "0.20")
        ), 5, true);

        assertEquals(BIN_A, plan.allocations().get(0).binId());
    }

    @Test
    void insufficientSingleBin_isSplitAndReportsUnallocatedQuantity() {
        PutawayPlan plan = planner.plan(List.of(
                candidate(RACK_A, BIN_A, "RACK-A", "BIN-A", 1, false, 6, "0.10"),
                candidate(RACK_B, BIN_B, "RACK-B", "BIN-B", 2, false, 1, "0.20")
        ), 10, false);

        assertEquals(2, plan.allocations().size());
        assertEquals(6, plan.allocations().get(0).quantity());
        assertEquals(1, plan.allocations().get(1).quantity());
        assertEquals(3, plan.unallocatedQuantity());
    }

    @Test
    void enoughCapacity_isPreferredBeforePartialCandidateAfterSameSkuPriority() {
        PutawayPlan plan = planner.plan(List.of(
                candidate(RACK_A, BIN_A, "RACK-A", "BIN-A", 1, false, 3, "0.05"),
                candidate(RACK_B, BIN_B, "RACK-B", "BIN-B", 2, false, 10, "0.40")
        ), 5, false);

        assertEquals(BIN_B, plan.allocations().get(0).binId());
        assertEquals(5, plan.allocations().get(0).quantity());
    }

    @Test
    void equalCandidates_useStableRackThenBinCodeTieBreak() {
        PutawayPlan plan = planner.plan(List.of(
                candidate(RACK_B, BIN_B, "RACK-B", "BIN-1", 1, false, 5, null),
                candidate(RACK_A, BIN_A, "RACK-A", "BIN-9", 1, false, 5, null)
        ), 5, false);

        assertEquals(BIN_A, plan.allocations().get(0).binId());
    }

    private PutawayCandidate candidate(UUID rackId, UUID binId,
                                       String rackCode, String binCode,
                                       int positionZ, boolean containsSku,
                                       int maxQuantity, String remainingRatio) {
        return new PutawayCandidate(rackId, binId, rackCode, binCode,
                BigDecimal.valueOf(positionZ), containsSku, maxQuantity,
                remainingRatio == null ? null : new BigDecimal(remainingRatio));
    }
}
