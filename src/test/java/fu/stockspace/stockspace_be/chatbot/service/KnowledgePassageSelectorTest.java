package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgePassageSelectorTest {

    @Test
    void selectsRelevantPassageAndKeepsSourceOffsets() {
        String content = "Đoạn giới thiệu không liên quan.\n\n"
                + "Phí kiểm định kho lạnh là 100.000 đồng mỗi lần và áp dụng theo bảng giá hiện hành.\n\n"
                + "Đoạn kết thúc.";

        KnowledgePassageSelector.Passage passage =
                KnowledgePassageSelector.selectBest("Bảng phí", content, "phí kiểm định kho lạnh");

        assertTrue(passage.text().contains("100.000"));
        assertTrue(passage.startOffset() >= 0);
        assertTrue(passage.endOffset() > passage.startOffset());
    }
}
