package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerEvidenceVerifierTest {

    @Test
    void acceptsNumbersPresentInToolEvidenceWithVietnameseGrouping() {
        ToolExecutionTrace trace = new ToolExecutionTrace(
                "getPublicWarehouseLayout",
                Map.of(),
                "{\"floorAreaM2\":1000,\"widthMeters\":20}",
                true,
                1
        );

        AnswerEvidenceVerifier.Verification verification = AnswerEvidenceVerifier.verify(
                "Diện tích là 1.000 m², chiều rộng 20 m.", List.of(trace));

        assertTrue(verification.valid());
    }

    @Test
    void acceptsHumanFriendlyMoneyUnitsWhenEvidenceUsesBaseCurrency() {
        ToolExecutionTrace trace = new ToolExecutionTrace(
                "searchWarehouses",
                Map.of(),
                "{\"monthlyPriceVnd\":15000000}",
                true,
                1
        );

        assertTrue(AnswerEvidenceVerifier.verify(
                "Giá thuê là 15 triệu đồng mỗi tháng.", List.of(trace)).valid());
    }

    @Test
    void rejectsUnsupportedMaterialNumber() {
        ToolExecutionTrace trace = new ToolExecutionTrace(
                "searchWarehouses", Map.of(), "{\"warehouses\":[],\"total\":0}", true, 1);

        AnswerEvidenceVerifier.Verification verification = AnswerEvidenceVerifier.verify(
                "Kho này có giá 15.000.000 VND/tháng.", List.of(trace));

        assertFalse(verification.valid());
        assertEquals("15.000.000", verification.unsupportedNumber());
        assertTrue(AnswerEvidenceVerifier.guard(
                "Kho này có giá 15.000.000 VND/tháng.", List.of(trace))
                .contains("chưa thể xác minh"));
    }

    @Test
    void ignoresNumberedAnswerListMarkers() {
        ToolExecutionTrace trace = new ToolExecutionTrace(
                "searchSystemPolicy", Map.of(), "{\"policies\":[{}]}", true, 1);

        assertTrue(AnswerEvidenceVerifier.verify(
                "1. Bước đầu tiên\n2. Bước tiếp theo", List.of(trace)).valid());
    }
}
