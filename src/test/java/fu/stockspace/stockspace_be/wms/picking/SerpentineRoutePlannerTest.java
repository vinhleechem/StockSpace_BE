package fu.stockspace.stockspace_be.wms.picking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerpentineRoutePlannerTest {

    private static final UUID SKU_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SKU_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID RACK_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID RACK_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID RACK_C = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID RACK_D = UUID.fromString("00000000-0000-0000-0000-000000000204");
    private static final UUID BIN_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID BIN_B = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID BIN_C = UUID.fromString("00000000-0000-0000-0000-000000000303");
    private static final UUID BIN_D = UUID.fromString("00000000-0000-0000-0000-000000000304");

    private final SerpentineRoutePlanner planner = new SerpentineRoutePlanner();

    @Test
    void plan_groupsSameBinAndAlternatesRackDirectionByYRow() {
        PickRoutePlan plan = planner.plan(List.of(
                candidate(SKU_1, "batch-4", RACK_D, "RACK-D", 5, 2, BIN_D, "BIN-D", 5, 1, 4),
                candidate(SKU_1, "batch-2", RACK_B, "RACK-B", 5, 1, BIN_B, "BIN-B", 1, 1, 2),
                candidate(SKU_2, "batch-3", RACK_A, "RACK-A", 1, 1, BIN_A, "BIN-A", 1, 1, 3),
                candidate(SKU_1, "batch-1", RACK_A, "RACK-A", 1, 1, BIN_A, "BIN-A", 1, 1, 5),
                candidate(SKU_1, "batch-5", RACK_C, "RACK-C", 1, 2, BIN_C, "BIN-C", 1, 1, 6)
        ));

        assertEquals(List.of(RACK_A, RACK_B, RACK_D, RACK_C),
                plan.stops().stream().map(PickRouteStop::rackId).toList());
        assertEquals(List.of(BIN_A, BIN_B, BIN_D, BIN_C),
                plan.stops().stream().map(PickRouteStop::binId).toList());
        assertEquals(List.of(1, 2, 3, 4),
                plan.stops().stream().map(PickRouteStop::sequence).toList());
        assertEquals(2, plan.stops().get(0).allocations().size());
        assertEquals(8, plan.stops().get(0).allocations().stream()
                .mapToInt(PickRouteAllocation::quantity).sum());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test
    void plan_ordersBinsByRackDirectionThenShelfCodeAndId() {
        UUID firstBin = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID secondBin = UUID.fromString("00000000-0000-0000-0000-000000000402");
        UUID thirdBin = UUID.fromString("00000000-0000-0000-0000-000000000403");

        PickRoutePlan plan = planner.plan(List.of(
                candidate(SKU_1, "batch-1", RACK_A, "RACK-A", 1, 1,
                        thirdBin, "BIN-C", 3, 1, 1),
                candidate(SKU_1, "batch-2", RACK_A, "RACK-A", 1, 1,
                        firstBin, "BIN-A", 1, 2, 1),
                candidate(SKU_1, "batch-3", RACK_A, "RACK-A", 1, 1,
                        secondBin, "BIN-B", 1, 1, 1)
        ));

        assertEquals(List.of(secondBin, firstBin, thirdBin),
                plan.stops().stream().map(PickRouteStop::binId).toList());
    }

    @Test
    void plan_usesStableFallbackAndReportsMissingCoordinates() {
        UUID codedRack = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID uncodedRack = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID codedBin = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID uncodedBin = UUID.fromString("00000000-0000-0000-0000-000000000602");

        PickRoutePlan plan = planner.plan(List.of(
                candidate(SKU_1, "batch-1", uncodedRack, null, null, null,
                        uncodedBin, null, null, null, 1),
                candidate(SKU_1, "batch-2", codedRack, "RACK-CODE", null, null,
                        codedBin, "BIN-CODE", null, null, 1)
        ));

        assertEquals(codedRack, plan.stops().get(0).rackId());
        assertEquals(4, plan.warnings().size());
        assertTrue(plan.warnings().stream().allMatch(warning -> warning.contains("fallback")));
    }

    @Test
    void plan_rejectsInvalidAllocationInsteadOfDroppingQuantity() {
        assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(
                candidate(SKU_1, "batch-1", RACK_A, "RACK-A", 1, 1,
                        BIN_A, "BIN-A", 1, 1, 0)
        )));
    }

    private PickRouteCandidate candidate(UUID skuId, String batchId,
                                         UUID rackId, String rackCode,
                                         Integer rackX, Integer rackY,
                                         UUID binId, String binCode,
                                         Integer binX, Integer shelfLevel,
                                         int quantity) {
        return new PickRouteCandidate(
                skuId,
                UUID.nameUUIDFromBytes(batchId.getBytes(StandardCharsets.UTF_8)),
                rackId,
                rackCode,
                rackX == null ? null : BigDecimal.valueOf(rackX),
                rackY == null ? null : BigDecimal.valueOf(rackY),
                binId,
                binCode,
                binX == null ? null : BigDecimal.valueOf(binX),
                shelfLevel,
                quantity);
    }
}
