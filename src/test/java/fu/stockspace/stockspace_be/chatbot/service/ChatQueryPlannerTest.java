package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        assertEquals("Bình Dương", plan.filters().get("keyword"));
        assertEquals("Tìm kho lạnh ở Bình Dương giá theo m² dưới 15 triệu, tối thiểu 500 m²",
                plan.filters().get("semanticQuery"));
        assertEquals("PER_SQUARE_METER_MONTHLY", plan.filters().get("pricingType"));
        assertEquals(BigDecimal.valueOf(15_000_000L), plan.filters().get("maxRentalPrice"));
        assertEquals(BigDecimal.valueOf(500L), plan.filters().get("minCapacity"));
        assertTrue(plan.promptContext().contains("<user-query>"));
        assertFalse(plan.promptContext().contains("Tìm kho lạnh"));
    }

    @Test
    void canonicalizesHoChiMinhAliasesForStructuredWarehouseSearch() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Tìm kho phù hợp ở TP.HCM");

        assertEquals("Hồ Chí Minh", plan.filters().get("province"));
        assertEquals("Hồ Chí Minh", plan.filters().get("keyword"));
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
    void doesNotFilterPricingModelWhenUserAsksToCompareMonthlyAndSquareMeterPrice() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Kho FPT SOFTWARE giá bao nhiêu và tính theo tháng hay m2");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertFalse(plan.filters().containsKey("pricingType"));
    }

    @Test
    void doesNotTreatNamedWarehousePriceQuestionAsPricingFilter() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Kho FPT SOFTWARE giá bao nhiêu theo tháng");

        assertFalse(plan.filters().containsKey("pricingType"));
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

    @Test
    void treatsWardOrAmbiguousLocationAsSearchKeywordInsteadOfProvince() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan("kho nào ở an phú");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("an phú", plan.filters().get("keyword"));
        assertFalse(plan.filters().containsKey("province"));
        assertEquals("kho nào ở an phú", plan.filters().get("semanticQuery"));
    }

    @Test
    void recognizesLocationOnlyFollowUpAsWarehouseSearch() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan("ở phường an phú");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("phường an phú", plan.filters().get("keyword"));
        assertFalse(plan.filters().containsKey("province"));
    }

    @Test
    void recognizesAmbiguousLocationOnlyFollowUpWithoutProvinceAssumption() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan("ở an phú");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("an phú", plan.filters().get("keyword"));
        assertFalse(plan.filters().containsKey("province"));
    }

    @Test
    void routesSystemInfoAndWarehouseTypeQuestionsToGroundedTools() {
        assertEquals("searchSystemPolicy",
                ChatQueryPlanner.plan("StockSpace có những chức năng gì?").requiredTool());
        assertEquals("getWarehouseTypes",
                ChatQueryPlanner.plan("Có những loại kho nào?").requiredTool());
    }

    @Test
    void keepsPublicCapacityFilterOnWarehouseSearchRoute() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Tìm kho có sức chứa tối thiểu 1000 m2");

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals(new BigDecimal("1000"), plan.filters().get("minCapacity"));
    }

    @Test
    void carriesPreviousWarehouseContextIntoNaturalFollowUp() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Còn kho nào rẻ hơn không?",
                Map.of(
                        "keyword", "Bình Dương",
                        "province", "Bình Dương",
                        "semanticQuery", "Tìm kho lạnh ở Bình Dương"
                ));

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("Bình Dương", plan.filters().get("keyword"));
        assertEquals("Bình Dương", plan.filters().get("province"));
        assertEquals("PRICE_ASC", plan.filters().get("sortBy"));
        assertTrue(String.valueOf(plan.filters().get("semanticQuery"))
                .contains("Tìm kho lạnh ở Bình Dương"));
    }

    @Test
    void changesLocationWithoutDroppingPreviousWarehouseNeed() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Ở Đồng Nai",
                Map.of(
                        "keyword", "Bình Dương",
                        "semanticQuery", "Tìm kho lạnh giá theo m2"
                ));

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals("Đồng Nai", plan.filters().get("province"));
        assertEquals("Đồng Nai", plan.filters().get("keyword"));
        assertTrue(String.valueOf(plan.filters().get("semanticQuery"))
                .contains("Tìm kho lạnh giá theo m2"));
    }

    @Test
    void parsesRelativePriceBoundInContextualFollowUp() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Còn kho nào rẻ hơn 10 triệu không?",
                Map.of("keyword", "kho lạnh ở Bình Dương"));

        assertEquals(ChatQueryPlanner.Intent.WAREHOUSE_SEARCH, plan.intent());
        assertEquals(new BigDecimal("10000000"), plan.filters().get("maxRentalPrice"));
        assertEquals("PRICE_ASC", plan.filters().get("sortBy"));
    }

    @Test
    void parsesHigherRelativePriceBound() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Tìm kho đắt hơn 10 triệu ở Đồng Nai");

        assertEquals(new BigDecimal("10000000"), plan.filters().get("minRentalPrice"));
        assertEquals("PRICE_DESC", plan.filters().get("sortBy"));
    }

    @Test
    void canResetPreviousWarehouseFilters() {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(
                "Bỏ bộ lọc, tìm kho ở Cần Thơ",
                Map.of(
                        "province", "Bình Dương",
                        "minRentalPrice", new BigDecimal("5000000"),
                        "keyword", "Bình Dương"
                ));

        assertEquals("Cần Thơ", plan.filters().get("province"));
        assertFalse(plan.filters().containsKey("minRentalPrice"));
    }
}
