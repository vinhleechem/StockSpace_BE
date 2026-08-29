package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
class ContractLayoutServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private WarehouseLayoutService warehouseLayoutService;
    @Mock private NotificationService notificationService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ContractService contractService;

    private UUID ownerId;
    private UUID tenantId;
    private UUID warehouseId;
    private UUID contractId;
    private User owner;
    private User tenant;
    private Warehouse warehouse;
    private RentalContract contract;
    private WarehouseLayoutResponse layout;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        owner = User.builder().id(ownerId).fullName("Owner").build();
        tenant = User.builder().id(tenantId).fullName("Tenant").build();
        warehouse = Warehouse.builder().id(warehouseId).owner(owner).name("Warehouse A").build();
        contract = RentalContract.builder()
                .id(contractId)
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.DRAFT)
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("20"))
                .leasedHeight(new BigDecimal("5"))
                .build();
        layout = WarehouseLayoutResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .tenantId(tenantId)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of())
                .build();
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    }

    @Test
    void ownerCanReadContractScopedLayout() {
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));

        WarehouseLayoutResponse response = contractService.getOwnerContractLayout(ownerId, contractId);

        assertEquals(layout, response);
        verify(warehouseLayoutService).findActiveTenantLayoutForContract(warehouseId, tenantId);
    }

    @Test
    void ownerCanUpdateDraftLayoutWithoutChangingContractDimensions() {
        BulkLayoutSaveRequest request = requestWithContractDimensions();
        when(warehouseLayoutService.saveContractLayout(warehouseId, tenantId, request)).thenReturn(layout);
        when(warehouseLayoutService.stabilizeLayoutSnapshot(layout)).thenReturn(layout);
        when(contractRepository.save(contract)).thenReturn(contract);

        WarehouseLayoutResponse response = contractService.updateOwnerContractLayout(ownerId, contractId, request);

        assertEquals(layout, response);
        verify(warehouseLayoutService).saveContractLayout(warehouseId, tenantId, request);
        verify(contractRepository).save(contract);
        assertEquals(tenantId, contract.getTenant().getId());
    }

    @Test
    void ownerCannotUpdateLayoutInAnotherContractState() {
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);

        assertThrows(BadRequestException.class,
                () -> contractService.updateOwnerContractLayout(
                        ownerId, contractId, requestWithContractDimensions()));

        verify(warehouseLayoutService, never()).saveContractLayout(any(), any(), any());
        verify(contractRepository, never()).save(any(RentalContract.class));
    }

    @Test
    void ownerCannotChangeContractDimensionsThroughLayoutEndpoint() {
        BulkLayoutSaveRequest request = BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("9"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .build();

        assertThrows(BadRequestException.class,
                () -> contractService.updateOwnerContractLayout(ownerId, contractId, request));
        verify(warehouseLayoutService, never()).saveContractLayout(any(), any(), any());
    }

    @Test
    void tenantCanReadSubmittedContractLayoutWithoutSubscriptionChecks() {
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));

        WarehouseLayoutResponse response = contractService.getTenantContractLayout(tenantId, contractId);

        assertEquals(layout, response);
        verify(warehouseLayoutService).findActiveTenantLayoutForContract(warehouseId, tenantId);
    }

    @Test
    void tenantCanReadExpiredSnapshotAfterOperationalLayoutIsArchived() throws Exception {
        contract.setStatus(ContractStatus.EXPIRED);
        contract.setLayoutSnapshot(objectMapper.writeValueAsString(layout));

        WarehouseLayoutResponse response = contractService.getTenantContractLayout(tenantId, contractId);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(new BigDecimal("10"), response.getWidth());
    }

    @Test
    void outsiderCannotReadTenantContractLayout() {
        assertThrows(ForbiddenException.class,
                () -> contractService.getTenantContractLayout(UUID.randomUUID(), contractId));
        verify(warehouseLayoutService, never()).findActiveTenantLayoutForContract(any(), any());
    }

    @Test
    void tenantCannotReadDraftLayoutBeforeOwnerSubmitsContract() {
        assertThrows(BadRequestException.class,
                () -> contractService.getTenantContractLayout(tenantId, contractId));
        verify(warehouseLayoutService, never()).findActiveTenantLayoutForContract(any(), any());
    }

    private BulkLayoutSaveRequest requestWithContractDimensions() {
        return BulkLayoutSaveRequest.builder()
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .build();
    }
}
