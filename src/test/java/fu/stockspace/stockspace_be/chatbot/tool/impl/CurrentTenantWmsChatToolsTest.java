package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentTenantWmsChatToolsTest {

    @Mock
    private WarehouseService warehouseService;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID warehouseId;
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
    }

    @Test
    void ownerContactRequiresLoginAndUsesPublishedWarehouseService() throws Exception {
        GetWarehouseOwnerContactTool tool = new GetWarehouseOwnerContactTool(objectMapper, warehouseService);
        JsonNode guestResult = objectMapper.readTree(
                tool.execute(Map.of("warehouseId", warehouseId.toString()), null));
        assertTrue(guestResult.has("error"));
        verifyNoInteractions(warehouseService);

        when(warehouseService.getOwnerContact(warehouseId)).thenReturn(WarehouseOwnerContactResponse.builder()
                .warehouseId(warehouseId)
                .ownerId(UUID.randomUUID())
                .ownerName("Nguyễn Văn A")
                .phone("0901234567")
                .build());
        JsonNode tenantResult = objectMapper.readTree(
                tool.execute(Map.of("warehouseId", warehouseId.toString()), userId));

        assertEquals("Nguyễn Văn A", tenantResult.get("contactName").asText());
        assertEquals("0901234567", tenantResult.get("phone").asText());
        assertFalse(tenantResult.has("ownerId"));
        assertFalse(tenantResult.has("warehouseId"));
        verify(warehouseService).getOwnerContact(warehouseId);
    }

}
