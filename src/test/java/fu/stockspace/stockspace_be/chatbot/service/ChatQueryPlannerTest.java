package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatQueryPlannerTest {

    @Test
    void extractsWarehouseSearchFiltersBeforeModelToolCall() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Tìm kho lạnh ở Bình Dương giá theo m² dưới 15 triệu, tối thiểu 500 m²");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("searchWarehouses", plan.requiredTool());
        assertEquals("Bình Dương", plan.filters().get("province"));
        assertEquals("PER_SQUARE_METER_MONTHLY", plan.filters().get("pricingType"));
        assertEquals(BigDecimal.valueOf(15_000_000L), plan.filters().get("maxRentalPrice"));
        assertEquals(BigDecimal.valueOf(500L), plan.filters().get("minCapacity"));
        assertTrue(plan.promptContext().contains("<user-query>"));
        assertFalse(plan.promptContext().contains("Tìm kho lạnh"));
    }

    @Test
    void doesNotTurnOperationalStockQuestionIntoPublicWarehouseSearch() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan("Xem tồn kho hiện tại");

        assertEquals(ChatQueryPlanner.Intent.NONE, plan.intent());
    }

    @Test
    void routesRentalPolicyToItsMandatoryTool() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan("Quy trình thuê kho và đặt cọc");

        assertEquals(ChatQueryPlanner.Intent.RENTAL_POLICY, plan.intent());
        assertEquals("searchSystemPolicy", plan.requiredTool());
        assertTrue(plan.requiresEvidence());
    }

    @Test
    void extractsPriceRangeAndVerificationFilter() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Tìm kho ở Bình Dương giá từ 5 đến 15 triệu, đã xác minh");

        assertEquals(new BigDecimal("5000000"), plan.filters().get("minRentalPrice"));
        assertEquals(new BigDecimal("15000000"), plan.filters().get("maxRentalPrice"));
        assertEquals(true, plan.filters().get("isVerified"));
    }

    @Test
    void decomposesCompoundRentalQuestionIntoIndependentPolicyQueries() {
        List<ChatQueryPlanner.SubQuery> queries = ChatQueryPlanner.decompose(
                "Gia hạn hợp đồng và bảo hiểm hàng hóa"
        );

        assertEquals(2, queries.size());
        assertEquals("searchSystemPolicy", queries.get(0).requiredTool());
        assertEquals("searchSystemPolicy", queries.get(1).requiredTool());
        assertEquals("INSURANCE", queries.get(1).arguments().get("category"));
    }
}
