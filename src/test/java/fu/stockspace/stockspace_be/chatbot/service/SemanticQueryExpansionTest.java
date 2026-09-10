package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticQueryExpansionTest {

    @Test
    void expandsWarehouseSynonymsAndVietnameseDiacritics() {
        Set<String> aliases = SemanticQueryExpansion.expand("Tìm kho mát bảo quản thực phẩm");

        assertTrue(aliases.contains("kho lanh"));
        assertTrue(aliases.contains("kho mat"));
        assertTrue(aliases.contains("bao quan thuc pham"));
        assertTrue(aliases.contains("nhiet do"));

        Set<String> technologyAliases = SemanticQueryExpansion.expand("hàng công nghệ");
        assertTrue(technologyAliases.contains("dien tu"));
        assertTrue(technologyAliases.contains("linh kien"));
    }

    @Test
    void toleratesSmallPhraseTypos() {
        Set<String> aliases = SemanticQueryExpansion.expand("kho lnh");

        assertTrue(aliases.contains("kho lanh"));
        assertTrue(aliases.contains("kho mat"));
    }

    @Test
    void expandsRentalPolicyVocabulary() {
        Set<String> aliases = SemanticQueryExpansion.expand("phí đặt cọc thuê kho");

        assertTrue(aliases.contains("tien coc"));
        assertTrue(aliases.contains("ky quy"));
        assertTrue(aliases.contains("tam ung"));
    }
}
