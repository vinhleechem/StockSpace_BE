package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseSearchRequest;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicWarehouseSearchTest {

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private WarehouseTypeService warehouseTypeService;

    @Mock
    private WarehouseLayoutService warehouseLayoutService;

    @Test
    void forwardsCombinedPriceCapacityPaginationAndSortFilters() {
        PagedResponse<WarehouseResponse> emptyPage = emptyPage(2, 20);
        when(warehouseService.searchWarehouses(any(WarehouseSearchRequest.class), eq(2), eq(20),
                eq("capacity"), eq("asc"))).thenReturn(emptyPage);

        ResponseEntity<?> response = controller().search(
                "  kho khô  ",
                null,
                null,
                new BigDecimal("1000000"),
                new BigDecimal("5000000"),
                new BigDecimal("50"),
                2,
                20,
                "capacity",
                "asc"
        );

        ArgumentCaptor<WarehouseSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(WarehouseSearchRequest.class);
        verify(warehouseService).searchWarehouses(requestCaptor.capture(), eq(2), eq(20),
                eq("capacity"), eq("asc"));

        WarehouseSearchRequest request = requestCaptor.getValue();
        assertEquals("  kho khô  ", request.getKeyword());
        assertEquals(new BigDecimal("1000000"), request.getMinRentalPrice());
        assertEquals(new BigDecimal("5000000"), request.getMaxRentalPrice());
        assertEquals(new BigDecimal("50"), request.getMinCapacity());
        assertNotNull(response.getBody());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void acceptsLegacyPriceAliasesDuringCompatibilityWindow() {
        when(warehouseService.searchWarehouses(any(WarehouseSearchRequest.class), eq(0), eq(10),
                eq("createdAt"), eq("desc"))).thenReturn(emptyPage(0, 10));

        controller().search(
                null,
                new BigDecimal("100"),
                new BigDecimal("200"),
                null,
                null,
                null,
                0,
                10,
                "createdAt",
                "desc"
        );

        ArgumentCaptor<WarehouseSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(WarehouseSearchRequest.class);
        verify(warehouseService).searchWarehouses(requestCaptor.capture(), eq(0), eq(10),
                eq("createdAt"), eq("desc"));
        assertEquals(new BigDecimal("100"), requestCaptor.getValue().getMinRentalPrice());
        assertEquals(new BigDecimal("200"), requestCaptor.getValue().getMaxRentalPrice());
    }

    @Test
    void rejectsInvertedPriceRangeBeforeQueryingService() {
        assertThrows(BadRequestException.class, () -> controller().search(
                null,
                null,
                null,
                new BigDecimal("200"),
                new BigDecimal("100"),
                null,
                0,
                10,
                "createdAt",
                "desc"
        ));

        verifyNoInteractions(warehouseService);
    }

    private PublicWarehouseController controller() {
        return new PublicWarehouseController(
                warehouseService,
                warehouseTypeService,
                warehouseLayoutService
        );
    }

    private PagedResponse<WarehouseResponse> emptyPage(int page, int size) {
        return PagedResponse.<WarehouseResponse>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();
    }
}
