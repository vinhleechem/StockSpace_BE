package fu.stockspace.stockspace_be.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryServiceTest {

    private ConversationMemoryService service;

    @BeforeEach
    void setUp() {
        service = new ConversationMemoryService(
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void remembersWarehouseFromSearchResultAndResolvesReferentialFollowUp() {
        UUID warehouseId = UUID.randomUUID();
        ConversationMemory memory = service.merge(
                ConversationMemory.empty(),
                List.of(new ToolExecutionTrace(
                        "searchWarehouses",
                        Map.of("keyword", "kho lạnh"),
                        "{\"warehouses\":[{"+
                                "\"id\":\"" + warehouseId + "\",\"name\":\"Kho lạnh Tân Trào\"}]}",
                        true,
                        12
                )));

        Map<String, Object> args = memory.enrichToolArguments(
                "getPublicWarehouseLayout", Map.of(), "bao nhiêu m2?");

        assertEquals(warehouseId.toString(), args.get("warehouseId"));
        assertTrue(memory.promptContext("bao nhiêu m2").contains(warehouseId.toString()));
    }

    @Test
    void doesNotGuessWhenMultipleWarehousesAreInMemory() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ConversationMemory memory = service.merge(
                ConversationMemory.empty(),
                List.of(new ToolExecutionTrace(
                        "searchWarehouses",
                        Map.of(),
                        "{\"warehouses\":["
                                + "{\"id\":\"" + first + "\",\"name\":\"Kho A\"},"
                                + "{\"id\":\"" + second + "\",\"name\":\"Kho B\"}]}",
                        true,
                        1
                )));

        assertTrue(memory.enrichToolArguments(
                "getPublicWarehouseLayout", Map.of(), "bao nhiêu m2?").isEmpty());
        assertEquals(second.toString(), memory.enrichToolArguments(
                "getPublicWarehouseLayout", Map.of(), "Kho B bao nhiêu m2?").get("warehouseId"));
    }

    @Test
    void invalidStoredMemoryIsIgnored() {
        assertTrue(service.read("not-json").isEmpty());
    }
}
