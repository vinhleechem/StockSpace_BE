package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentalIntentClassifierTest {

    @Test
    void routesRentalPolicyQuestionsToKnowledgeSearch() {
        RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(
                "Quy trình thuê kho và đặt cọc như thế nào?");

        assertEquals(RentalIntentClassifier.Route.SYSTEM_POLICY, intent.route());
        assertEquals("RENTAL_PROCESS", intent.category());
        assertEquals("searchSystemPolicy", intent.requiredTool());

        assertEquals(
                RentalIntentClassifier.Route.SYSTEM_POLICY,
                RentalIntentClassifier.classify("quy trinh thue kho va dat coc").route());
    }

    @Test
    void routesLiveFeesToCurrentRules() {
        RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(
                "Phí kiểm định hiện tại là bao nhiêu?");

        assertEquals(RentalIntentClassifier.Route.CURRENT_SYSTEM_RULES, intent.route());
        assertEquals("getCurrentSystemRules", intent.requiredTool());
    }

    @Test
    void routesPackagesAndPrivateDataSeparately() {
        assertEquals(
                RentalIntentClassifier.Route.SERVICE_PACKAGES,
                RentalIntentClassifier.classify("Cho tôi bảng giá các gói dịch vụ").route());
        assertEquals(
                RentalIntentClassifier.Route.MY_ACTIVE_SUBSCRIPTION,
                RentalIntentClassifier.classify("Gói của tôi còn hạn đến bao giờ?").route());
        assertEquals(
                RentalIntentClassifier.Route.MY_CONTRACTS,
                RentalIntentClassifier.classify("Hợp đồng của tôi sắp hết hạn chưa?").route());
        assertEquals(
                RentalIntentClassifier.Route.MY_CONTRACTS,
                RentalIntentClassifier.classify("Hợp đồng hiện tại của tôi là hợp đồng nào?").route());
        assertEquals(
                RentalIntentClassifier.Route.MY_CONTRACTS,
                RentalIntentClassifier.classify("Hợp đồng của tôi có thể gia hạn không?").route());
        assertEquals(
                RentalIntentClassifier.Route.SYSTEM_POLICY,
                RentalIntentClassifier.classify("Điều kiện gia hạn hợp đồng là gì?").route());
        assertEquals(
                "RENTAL_PROCESS",
                RentalIntentClassifier.classify("Điều kiện gia hạn hợp đồng là gì?").category());
        assertEquals(
                "FAQ",
                RentalIntentClassifier.classify("Điều kiện truy cập và quản lý WMS là gì?").category());
    }

    @Test
    void doesNotMisrouteWarehouseSearchOrOperationalStockQuestion() {
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Tìm kho lạnh ở Bình Dương").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Giá thuê kho ở Bình Dương").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Phí thuê kho bao nhiêu?").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Xem kho đang cho thuê").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Cần thuê kho ở Bình Dương").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Xem tồn kho hiện tại").route());
        assertEquals(
                RentalIntentClassifier.Route.NONE,
                RentalIntentClassifier.classify("Kho lạnh Tân Trào bao nhiêu m²?").route());
    }
}
