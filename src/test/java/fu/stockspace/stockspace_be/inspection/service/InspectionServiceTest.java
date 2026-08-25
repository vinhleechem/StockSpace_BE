package fu.stockspace.stockspace_be.inspection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.inspection.entity.InspectionReport;
import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import fu.stockspace.stockspace_be.inspection.repository.InspectionReportRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock private InspectionReportRepository inspectionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private UserRepository userRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private ObjectMapper objectMapper;
    @Mock private WalletService walletService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private NotificationService notificationService;

    @InjectMocks private InspectionService inspectionService;

    private UUID ownerId;
    private UUID warehouseId;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .owner(User.builder().id(ownerId).build())
                .name("Inspection warehouse")
                .status(WarehouseStatus.AVAILABLE)
                .isVerified(false)
                .build();
    }

    @Test
    void availableListingCanRequestInspectionRegardlessOfRentalOccupancy() {
        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(inspectionRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(systemConfigService.getBigDecimalValue("inspection_fee", new BigDecimal("40000")))
                .thenReturn(BigDecimal.ZERO);
        when(inspectionRepository.save(any(InspectionReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findFirstByRoles_Name(any())).thenReturn(Optional.empty());

        var response = inspectionService.requestInspection(ownerId, warehouseId);

        assertEquals(InspectionStatus.PENDING.name(), response.getStatus());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void inactiveListingCannotRequestInspection() {
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));

        assertThrows(BadRequestException.class,
                () -> inspectionService.requestInspection(ownerId, warehouseId));

        verify(inspectionRepository, never()).save(any());
    }
}
