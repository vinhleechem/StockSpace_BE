package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetServicePackagesToolTest {

    @Mock
    private ServicePackageService servicePackageService;

    @Test
    void listsPublicPackageDataWithoutInternalIdentifiers() throws Exception {
        ServicePackageResponse basic = ServicePackageResponse.builder()
                .id(UUID.randomUUID())
                .name("Cơ bản")
                .features("Quản lý 2 kho")
                .price(new BigDecimal("199000"))
                .durationDays(30)
                .maxStaff(2)
                .build();
        when(servicePackageService.getAllPackages()).thenReturn(List.of(basic));

        JsonNode result = new ObjectMapper().readTree(
                new GetServicePackagesTool(new ObjectMapper(), servicePackageService)
                        .execute(Map.of(), null));

        assertEquals(1, result.get("total").asInt());
        assertEquals("Cơ bản", result.at("/packages/0/name").asText());
        assertFalse(result.toString().contains("\"id\""));
        verify(servicePackageService).getAllPackages();
    }
}
