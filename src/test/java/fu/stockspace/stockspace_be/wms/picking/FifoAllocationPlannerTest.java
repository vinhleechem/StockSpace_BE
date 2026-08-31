package fu.stockspace.stockspace_be.wms.picking;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FifoAllocationPlannerTest {

    private static final UUID BATCH_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BATCH_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BATCH_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime DAY_1 = LocalDateTime.of(2026, 8, 1, 8, 0);
    private static final LocalDateTime DAY_2 = DAY_1.plusDays(1);

    private final FifoAllocationPlanner planner = new FifoAllocationPlanner();

    @Test
    void plan_selectsOldestBatchFirst() {
        FifoAllocationPlan plan = planner.plan(List.of(
                candidate(BATCH_2, 50, DAY_2, DAY_2),
                candidate(BATCH_1, 50, DAY_1, DAY_1)
        ), 40);

        assertEquals(List.of(BATCH_1), plan.allocations().stream()
                .map(FifoAllocation::stockBatchId).toList());
        assertEquals(40, plan.allocations().get(0).quantity());
        assertEquals(0, plan.shortageQuantity());
    }

    @Test
    void plan_splitsQuantityAcrossBatchesInFifoOrder() {
        FifoAllocationPlan plan = planner.plan(List.of(
                candidate(BATCH_1, 40, DAY_1, DAY_1),
                candidate(BATCH_2, 60, DAY_2, DAY_2)
        ), 75);

        assertEquals(2, plan.allocations().size());
        assertEquals(40, plan.allocations().get(0).quantity());
        assertEquals(35, plan.allocations().get(1).quantity());
        assertEquals(BATCH_1, plan.allocations().get(0).stockBatchId());
        assertEquals(BATCH_2, plan.allocations().get(1).stockBatchId());
        assertEquals(0, plan.shortageQuantity());
    }

    @Test
    void plan_returnsExactQuantityWithoutShortage() {
        FifoAllocationPlan plan = planner.plan(List.of(
                candidate(BATCH_1, 25, DAY_1, DAY_1)
        ), 25);

        assertEquals(25, plan.allocations().get(0).quantity());
        assertEquals(0, plan.shortageQuantity());
    }

    @Test
    void plan_reportsShortageWhenStockIsInsufficient() {
        FifoAllocationPlan plan = planner.plan(List.of(
                candidate(BATCH_1, 20, DAY_1, DAY_1),
                candidate(BATCH_2, 10, DAY_2, DAY_2)
        ), 50);

        assertEquals(2, plan.allocations().size());
        assertEquals(20, plan.shortageQuantity());
    }

    @Test
    void plan_ignoresZeroInactiveDeletedAndMalformedCandidates() {
        FifoAllocationPlan plan = planner.plan(List.of(
                candidate(BATCH_1, 0, DAY_1, DAY_1),
                new FifoCandidate(BATCH_2, 100, DAY_1, DAY_1, false, false),
                new FifoCandidate(BATCH_3, 100, DAY_1, DAY_1, true, true),
                new FifoCandidate(null, 100, DAY_1, DAY_1, true, false)
        ), 10);

        assertEquals(0, plan.allocations().size());
        assertEquals(10, plan.shortageQuantity());
    }

    @Test
    void plan_usesCreatedAtThenBatchIdAsTieBreakers() {
        UUID earlierBatchId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID laterBatchId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        LocalDateTime sameArrival = DAY_1;
        LocalDateTime earlierCreatedAt = DAY_1.plusHours(1);
        LocalDateTime laterCreatedAt = DAY_1.plusHours(2);

        FifoAllocationPlan createdAtPlan = planner.plan(List.of(
                candidate(laterBatchId, 5, sameArrival, laterCreatedAt),
                candidate(earlierBatchId, 5, sameArrival, earlierCreatedAt)
        ), 5);
        assertEquals(earlierBatchId, createdAtPlan.allocations().get(0).stockBatchId());

        FifoAllocationPlan idPlan = planner.plan(List.of(
                candidate(laterBatchId, 5, sameArrival, earlierCreatedAt),
                candidate(earlierBatchId, 5, sameArrival, earlierCreatedAt)
        ), 5);
        assertEquals(earlierBatchId, idPlan.allocations().get(0).stockBatchId());
    }

    @Test
    void plan_rejectsNonPositiveRequestedQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(List.of(), 0));
    }

    private FifoCandidate candidate(UUID batchId, int quantity,
                                    LocalDateTime arrivalDate,
                                    LocalDateTime createdAt) {
        return new FifoCandidate(batchId, quantity, arrivalDate, createdAt, true, false);
    }
}
