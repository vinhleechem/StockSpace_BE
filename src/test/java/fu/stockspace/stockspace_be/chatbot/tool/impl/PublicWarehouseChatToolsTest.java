package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicWarehouseChatToolsTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void getWarehouseDetailReadsOnlyPublicAvailableWarehouse() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho Available")
                .address("Thủ Đức")
                .description("Kho khô")
                .capacity(new BigDecimal("250"))
                .rentalPricingType(RentalPricingType.FIXED_MONTHLY)
                .rentalPrice(new BigDecimal("15000000"))
                .status(WarehouseStatus.AVAILABLE)
                .isVerified(true)
                .build();
        when(warehouseRepository.findPublicAvailableById(warehouseId))
                .thenReturn(Optional.of(warehouse));

        JsonNode result = objectMapper.readTree(
                new GetWarehouseDetailTool(warehouseRepository, objectMapper)
                        .execute(Map.of("warehouseId", warehouseId.toString()), null));

        assertEquals(warehouseId.toString(), result.get("id").asText());
        assertEquals("15000000", result.get("rentalPrice").asText());
        assertEquals("FIXED_MONTHLY", result.get("rentalPricingType").asText());
        assertEquals("Sẵn sàng cho thuê", result.get("status").asText());
        assertFalse(result.toString().contains("AVAILABLE"));
        assertFalse(result.has("pricePerMonth"));
        assertFalse(result.toString().contains("ownerPhone"));
        verify(warehouseRepository).findPublicAvailableById(warehouseId);
        verify(warehouseRepository, never()).findById(warehouseId);
    }

    @Test
    void getWarehouseDetailDoesNotExposeUnavailableWarehouse() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        JsonNode result = objectMapper.readTree(
                new GetWarehouseDetailTool(warehouseRepository, objectMapper)
                        .execute(Map.of("warehouseId", warehouseId.toString()), null));

        assertTrue(result.has("error"));
        assertFalse(result.toString().contains("status"));
        verify(warehouseRepository).findPublicAvailableById(warehouseId);
    }

    @Test
    void searchWarehousesRejectsNegativeFiltersWithoutQueryingDatabase() throws Exception {
        JsonNode result = objectMapper.readTree(
                new SearchWarehousesTool(warehouseRepository, objectMapper)
                        .execute(Map.of("minArea", -1), null));

        assertEquals("Diện tích tối thiểu không được là số âm", result.get("error").asText());
        verifyNoInteractions(warehouseRepository);
    }

    @Test
    void searchWarehousesRejectsInvertedPriceRangeWithoutQueryingDatabase() throws Exception {
        JsonNode result = objectMapper.readTree(
                new SearchWarehousesTool(warehouseRepository, objectMapper)
                        .execute(Map.of("minPrice", 200, "maxPrice", 100), null));

        assertEquals(
                "Giá thuê tối thiểu không được lớn hơn giá thuê tối đa",
                result.get("error").asText()
        );
        verifyNoInteractions(warehouseRepository);
    }

    @Test
    void searchWarehousesUsesStructuredAvailableFilters() throws Exception {
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho công khai")
                .address("Quận 7")
                .description("Kho khô")
                .capacity(new BigDecimal("100"))
                .rentalPricingType(RentalPricingType.PER_SQUARE_METER_MONTHLY)
                .rentalPrice(new BigDecimal("150"))
                .status(WarehouseStatus.AVAILABLE)
                .isVerified(true)
                .build();
        when(warehouseRepository.searchPublic(
                eq("%quận 7%"),
                eq(WarehouseStatus.AVAILABLE),
                eq(new BigDecimal("100")),
                eq(new BigDecimal("200")),
                eq(new BigDecimal("50")),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(warehouse)));

        JsonNode result = objectMapper.readTree(
                new SearchWarehousesTool(warehouseRepository, objectMapper).execute(Map.of(
                        "keyword", "  Quận 7  ",
                        "minPrice", 100,
                        "maxPrice", 200,
                        "minArea", 50
                ), null));

        assertEquals(1, result.get("total").asLong());
        assertTrue(result.get("warehouses").isArray());
        assertEquals("Sẵn sàng cho thuê", result.at("/warehouses/0/status").asText());
        assertFalse(result.toString().contains("AVAILABLE"));
        verify(warehouseRepository).searchPublic(
                eq("%quận 7%"),
                eq(WarehouseStatus.AVAILABLE),
                eq(new BigDecimal("100")),
                eq(new BigDecimal("200")),
                eq(new BigDecimal("50")),
                any(Pageable.class)
        );
    }

    @Test
    void searchWarehousesListsAvailableWarehousesWhenNoCriteriaAreGiven() throws Exception {
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho đang cho thuê")
                .address("Bình Thạnh")
                .capacity(new BigDecimal("80"))
                .rentalPricingType(RentalPricingType.FIXED_MONTHLY)
                .rentalPrice(new BigDecimal("12000000"))
                .status(WarehouseStatus.AVAILABLE)
                .build();
        when(warehouseRepository.searchPublic(
                eq(null),
                eq(WarehouseStatus.AVAILABLE),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(warehouse)));

        JsonNode result = objectMapper.readTree(
                new SearchWarehousesTool(warehouseRepository, objectMapper).execute(Map.of(), null));

        assertEquals(1, result.get("total").asLong());
        assertEquals("Kho đang cho thuê", result.at("/warehouses/0/name").asText());
        verify(warehouseRepository).searchPublic(
                eq(null),
                eq(WarehouseStatus.AVAILABLE),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        );
    }

    @Test
    void everyPublicToolSchemaUsesLowercaseJsonSchemaTypes() {
        Map<String, Object> searchSchema =
                new SearchWarehousesTool(warehouseRepository, objectMapper).getParameterSchema();
        Map<String, Object> detailSchema =
                new GetWarehouseDetailTool(warehouseRepository, objectMapper).getParameterSchema();
        Map<String, Object> loginSchema = new AskLoginPromptTool().getParameterSchema();

        assertEquals("object", searchSchema.get("type"));
        assertEquals("object", detailSchema.get("type"));
        assertEquals("object", loginSchema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> properties =
                (Map<String, Map<String, Object>>) searchSchema.get("properties");
        assertEquals("string", properties.get("keyword").get("type"));
        assertEquals("number", properties.get("minPrice").get("type"));
        assertEquals("number", properties.get("maxPrice").get("type"));
        assertEquals("number", properties.get("minArea").get("type"));
    }

    @Test
    void publicToolDescriptionsAndSchemasDoNotExposeRawWarehouseStatus() {
        SearchWarehousesTool searchTool =
                new SearchWarehousesTool(warehouseRepository, objectMapper);
        GetWarehouseDetailTool detailTool =
                new GetWarehouseDetailTool(warehouseRepository, objectMapper);

        String modelFacingText = searchTool.getDescription()
                + " "
                + detailTool.getDescription()
                + " "
                + detailTool.getParameterSchema();

        String normalized = modelFacingText.toUpperCase(Locale.ROOT);
        assertFalse(normalized.contains("AVAILABLE"));
        assertFalse(normalized.contains("PENDING"));
        assertFalse(normalized.contains("TENANT"));
        assertFalse(normalized.contains("OWNER"));
    }
}
