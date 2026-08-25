package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.TenantContractDecisionRequest;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContractReviewServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private DisputeTicketRepository disputeRepository;
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

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        contractId = UUID.randomUUID();
        owner = User.builder().id(ownerId).fullName("Owner").build();
        tenant = User.builder().id(tenantId).fullName("Tenant").build();
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .owner(owner)
                .name("Warehouse A")
                .status(WarehouseStatus.AVAILABLE)
                .build();
        contract = RentalContract.builder()
                .id(contractId)
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.PENDING_TENANT_CONFIRM)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .pricingType(fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType.FIXED_MONTHLY)
                .rentalPriceSnapshot(new BigDecimal("1000000"))
                .finalMonthlyRent(new BigDecimal("1000000"))
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("20"))
                .leasedHeight(new BigDecimal("5"))
                .leasedAreaM2(new BigDecimal("200"))
                .layoutSnapshot("{}")
                .build();
    }

    @Test
    void tenantCanConfirmDirectContractWithoutBookingOrWalletInteraction() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(false);

        RentalContractResponse response = contractService.confirmDirectContract(tenantId, contractId);

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertTrueConfirmed();
        assertNotNull(contract.getConfirmedAt());
        assertEquals(Boolean.FALSE, response.isCanManageWms());
        verify(warehouseService).lockWarehouseForContractSubmit(warehouseId);
        verify(warehouseService, never()).markAsRented(any());
        verifyNoInteractions(walletService, bookingRepository);
        verify(warehouseLayoutService, never()).cloneLayout(any(), any());
    }

    @Test
    void tenantCanRequestChangesWithTrimmedReasonAndOwnerIsNotified() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        TenantContractDecisionRequest request = decision("  Please correct the leased area  ");

        contractService.requestDirectContractChanges(tenantId, contractId, request);

        assertEquals(ContractStatus.CHANGES_REQUESTED, contract.getStatus());
        assertEquals("Please correct the leased area", contract.getChangeRequestReason());
        assertFalse(contract.isTenantConfirmed());
        verify(notificationService).push(
                eq(ownerId), eq("Rental contract changes requested"), any(), eq("CONTRACT_CHANGES_REQUESTED"));
        verifyNoInteractions(walletService, bookingRepository);
    }

    @Test
    void tenantCanRejectDirectContractAndProposalIsArchivedWhenNoActiveContractExists() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(false);

        contractService.rejectDirectContract(tenantId, contractId, decision("Terms are incorrect"));

        assertEquals(ContractStatus.REJECTED, contract.getStatus());
        assertEquals("Terms are incorrect", contract.getRejectionReason());
        verify(warehouseLayoutService).archiveTenantLayout(warehouseId, tenantId);
        verifyNoInteractions(walletService, bookingRepository);
    }

    @Test
    void tenantCannotReviewAnotherTenantsContract() {
        stubContractLookup();

        assertThrows(ForbiddenException.class,
                () -> contractService.confirmDirectContract(UUID.randomUUID(), contractId));

        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
        verify(warehouseService, never()).lockWarehouseForContractSubmit(any());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void tenantCannotReviewContractAfterItLeavesPendingState() {
        stubContractLookup();
        contract.setStatus(ContractStatus.ACTIVE);

        assertThrows(BadRequestException.class,
                () -> contractService.requestDirectContractChanges(
                        tenantId, contractId, decision("Change the date")));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void confirmationRejectsAnOverlappingDirectContractBeforeActivation() {
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId),
                eq(contract.getStartDate()), eq(contract.getEndDate())))
                .thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> contractService.confirmDirectContract(tenantId, contractId));

        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
        verify(contractRepository, never()).save(any());
    }

    private void stubContractLookup() {
        when(contractRepository.findById(contractId)).thenReturn(java.util.Optional.of(contract));
    }

    private TenantContractDecisionRequest decision(String reason) {
        TenantContractDecisionRequest request = new TenantContractDecisionRequest();
        request.setReason(reason);
        return request;
    }

    private void assertTrueConfirmed() {
        org.junit.jupiter.api.Assertions.assertTrue(contract.isTenantConfirmed());
    }
}
