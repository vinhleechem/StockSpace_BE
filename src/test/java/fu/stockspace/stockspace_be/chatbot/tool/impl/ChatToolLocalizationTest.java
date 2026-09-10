package fu.stockspace.stockspace_be.chatbot.tool.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatToolLocalizationTest {

    @Test
    void localizesScheduledContractStatus() {
        assertEquals("Đã xác nhận, chờ ngày bắt đầu",
                ChatToolLocalization.contractStatus("SCHEDULED"));
    }
}
