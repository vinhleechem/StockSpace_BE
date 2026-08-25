package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.UpdateRentalContractRequest;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectContractSubmissionServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private fu.stockspace.stockspace_be.auth.repository.UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private WarehouseLayoutService warehouseLayoutService;
    @Mock private NotificationService notificationService;
    @Mock private SubscriptionService subscriptionService;
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
        owner = User.builder().id(ownerId).email("owner@example.com").fullName("Owner").build();
        tenant = User.builder().id(tenantId).email("tenant@example.com").fullName("Tenant").build();
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .owner(owner)
                .name("Warehouse A")
                .status(WarehouseStatus.AVAILABLE)
                .isVerified(true)
                .rentalPricingType(RentalPricingType.PER_SQUARE_METER_MONTHLY)
                .rentalPrice(new BigDecimal("200"))
                .build();
        contract = RentalContract.builder()
                .id(contractId)
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.DRAFT)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 10))
                .pricingType(RentalPricingType.PER_SQUARE_METER_MONTHLY)
                .rentalPriceSnapshot(new BigDecimal("200"))
                .finalMonthlyRent(new BigDecimal("40000"))
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("20"))
                .leasedHeight(new BigDecimal("5"))
                .leasedAreaM2(new BigDecimal("200"))
                .paperContractFiles("[\"https://example.com/paper.pdf\"]")
                .layoutSnapshot("{}")
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

    }

    @Test
    void ownerCanUpdateMutableTermsWithoutChangingDirectRelations() {
        stubContractLookup();
        UpdateRentalContractRequest request = new UpdateRentalContractRequest();
        request.setStartDate(LocalDate.of(2026, 10, 1));
        request.setEndDate(LocalDate.of(2026, 10, 10));
        request.setLeasedWidth(new BigDecimal("5"));
        request.setLeasedLength(new BigDecimal("10"));
        request.setLeasedHeight(new BigDecimal("5"));
        request.setOwnerNote("Updated terms");

        WarehouseLayoutResponse resizedLayout = WarehouseLayoutResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .tenantId(tenantId)
                .width(new BigDecimal("5"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of())
                .build();
        when(warehouseLayoutService.prepareTenantLayoutForDraft(
                warehouseId, tenantId, new BigDecimal("5"), new BigDecimal("10"),
                new BigDecimal("5"), false)).thenReturn(resizedLayout);
        when(warehouseLayoutService.stabilizeLayoutSnapshot(resizedLayout)).thenReturn(resizedLayout);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);
        when(contractRepository.save(contract)).thenReturn(contract);

        RentalContractResponse response = contractService.updateOwnerDraft(ownerId, contractId, request);

        assertEquals(tenantId, contract.getTenant().getId());
        assertEquals(warehouseId, contract.getWarehouse().getId());
        assertEquals(new BigDecimal("5"), contract.getLeasedWidth());
        assertEquals(new BigDecimal("10"), contract.getLeasedLength());
        assertEquals(new BigDecimal("10000"), contract.getFinalMonthlyRent());
        assertEquals("Updated terms", contract.getOwnerNote());
        assertEquals(Boolean.TRUE, response.isCanEdit());
        verify(warehouseLayoutService).prepareTenantLayoutForDraft(
                warehouseId, tenantId, new BigDecimal("5"), new BigDecimal("10"),
                new BigDecimal("5"), false);
    }

    @Test
    void submitLocksWarehouseRevalidatesAndMovesDraftToTenantConfirmation() {
        stubContractLookup();
        stubSubmitPrerequisites();

        RentalContractResponse response = contractService.submitOwnerContract(ownerId, contractId);

        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
        assertNotNull(contract.getSubmittedAt());
        assertEquals(ownerId, response.getOwnerId());
        assertEquals(Boolean.TRUE, response.isCanViewLayout());
        verify(warehouseService).lockWarehouseForContractSubmit(warehouseId);
        verify(contractRepository).existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId),
                eq(contract.getStartDate()), eq(contract.getEndDate()));
        verify(warehouseLayoutService).validateContractLayout(
                eq(layout), eq(warehouseId), eq(tenantId),
                eq(contract.getLeasedWidth()), eq(contract.getLeasedLength()), eq(contract.getLeasedHeight()));
    }

    @Test
    void submitScopesWarehouseOverlapToTheContractTenant() {
        User secondTenant = User.builder()
                .id(UUID.randomUUID())
                .email("second-tenant@example.com")
                .fullName("Second Tenant")
                .build();
        contract.setTenant(secondTenant);
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(secondTenant.getId()), eq(warehouseId),
                eq(contract.getStartDate()), eq(contract.getEndDate())))
                .thenReturn(false);
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, secondTenant.getId()))
                .thenReturn(Optional.of(layout));
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);

        contractService.submitOwnerContract(ownerId, contractId);

        verify(contractRepository).existsDirectDateOverlapForSubmit(
                eq(contractId), eq(secondTenant.getId()), eq(warehouseId),
                eq(contract.getStartDate()), eq(contract.getEndDate()));
        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
    }

    @Test
    void submitRejectsInclusiveDateOverlapBeforeChangingState() {
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> contractService.submitOwnerContract(ownerId, contractId));

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        verify(warehouseLayoutService, never()).validateContractLayout(
                any(), any(), any(), any(), any(), any());
        verify(contractRepository, never()).save(contract);
    }

    @Test
    void submitRequiresPaperContractFiles() {
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
        contract.setPaperContractFiles(null);

        assertThrows(BadRequestException.class,
                () -> contractService.submitOwnerContract(ownerId, contractId));

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        verify(warehouseLayoutService, never()).validateContractLayout(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownerCanResubmitAfterTenantRequestedChanges() {
        stubContractLookup();
        stubSubmitPrerequisites();
        contract.setStatus(ContractStatus.CHANGES_REQUESTED);
        contract.setChangeRequestReason("Please correct the leased area");

        contractService.submitOwnerContract(ownerId, contractId);

        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
        assertNull(contract.getChangeRequestReason());
    }

    @Test
    void submitRejectsInvalidLayoutBeforeSaving() {
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
        doThrow(new BadRequestException("invalid layout"))
                .when(warehouseLayoutService)
                .validateContractLayout(any(), any(), any(), any(), any(), any());

        assertThrows(BadRequestException.class,
                () -> contractService.submitOwnerContract(ownerId, contractId));

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        verify(contractRepository, never()).save(contract);
    }

    @Test
    void submitSnapshotsTenantLayoutInsteadOfLaterDefaultLayout() {
        stubContractLookup();
        stubSubmitPrerequisites();
        WarehouseLayoutResponse changedDefault = WarehouseLayoutResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .tenantId(null)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of("default-v2"))
                .build();
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(changedDefault);

        contractService.submitOwnerContract(ownerId, contractId);

        org.junit.jupiter.api.Assertions.assertTrue(contract.getLayoutSnapshot().contains(layout.getId().toString()));
        org.junit.jupiter.api.Assertions.assertFalse(contract.getLayoutSnapshot().contains("default-v2"));
    }

    @Test
    void tenantCannotManageWmsUntilActiveContractAndSubscription() {
        contract.setStatus(ContractStatus.ACTIVE);
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);

        RentalContractResponse response = contractService.mapToResponse(contract, tenantId);

        assertEquals(Boolean.TRUE, response.isCanManageWms());
        assertEquals(Boolean.TRUE, response.isCanViewLayout());
        verify(subscriptionService).hasActiveSubscription(tenantId);
    }

    private void stubSubmitPrerequisites() {
        when(contractRepository.save(contract)).thenReturn(contract);
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(layout);
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));
        when(warehouseLayoutService.stabilizeLayoutSnapshot(layout)).thenReturn(layout);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
    }

    private void stubContractLookup() {
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    }
}
