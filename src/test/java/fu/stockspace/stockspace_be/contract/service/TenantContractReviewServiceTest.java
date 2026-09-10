package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.TenantContractDecisionRequest;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContractReviewServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-09T17:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock private RentalContractRepository contractRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private fu.stockspace.stockspace_be.auth.repository.UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private WarehouseLayoutService warehouseLayoutService;
    @Mock private WarehouseRentalAvailabilityService warehouseRentalAvailabilityService;
    @Mock private NotificationService notificationService;
    @Mock private SubscriptionService subscriptionService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private Clock businessClock;

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
        lenient().when(businessClock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(businessClock.getZone()).thenReturn(BUSINESS_ZONE);

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
                .startDate(TODAY.plusDays(10))
                .endDate(TODAY.plusDays(20))
                .pricingType(fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType.FIXED_MONTHLY)
                .rentalPriceSnapshot(new BigDecimal("1000000"))
                .finalMonthlyRent(new BigDecimal("1000000"))
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("20"))
                .leasedHeight(new BigDecimal("5"))
                .leasedAreaM2(new BigDecimal("200"))
                .layoutSnapshot("{}")
                .build();
        lenient().when(warehouseRentalAvailabilityService.calculate(
                any(UUID.class), any(), any(LocalDate.class), any(LocalDate.class), any(BigDecimal.class)))
                .thenReturn(RentalAreaAvailability.of(
                        new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("200")));
        lenient().when(contractRepository.findByIdForUpdate(contractId))
                .thenReturn(java.util.Optional.of(contract));
    }

    @Test
    void tenantCanConfirmDirectContractWithoutWalletInteraction() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
        RentalContractResponse response = contractService.confirmDirectContract(tenantId, contractId);

        assertEquals(ContractStatus.SCHEDULED, contract.getStatus());
        assertNotNull(contract.getConfirmedAt());
        assertEquals(Boolean.FALSE, response.isCanManageWms());
        verify(warehouseService).lockWarehouseForContractSubmit(warehouseId);
        verifyNoInteractions(walletService);
        verify(warehouseLayoutService, never()).cloneLayout(any(), any());
    }

    @Test
    void tenantCanConfirmContractStartingTodayAsActive() {
        contract.setStartDate(TODAY);
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);

        contractService.confirmDirectContract(tenantId, contractId);

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test
    void tenantConfirmationSchedulesRenewalAndNotifiesBothParties() {
        RentalContract source = renewalSource();
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        contract.setStartDate(source.getEndDate().plusDays(1));
        contract.setEndDate(source.getEndDate().plusDays(8));
        contract.setRenewedFromContract(source);
        WarehouseLayoutResponse layout = WarehouseLayoutResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .tenantId(tenantId)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(java.util.List.of())
                .positions(java.util.List.of())
                .build();
        WarehouseLayoutResponse defaultLayout = WarehouseLayoutResponse.builder()
                .warehouseId(warehouseId)
                .isDefault(true)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(java.util.List.of())
                .positions(java.util.List.of())
                .build();
        when(contractRepository.findByIdForUpdate(source.getId())).thenReturn(java.util.Optional.of(source));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(java.util.Optional.of(contract));
        when(contractRepository.findBlockingRenewalsBySourceId(source.getId()))
                .thenReturn(java.util.List.of(contract));
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(defaultLayout);
        when(warehouseLayoutService.findActiveTenantLayoutForContract(warehouseId, tenantId))
                .thenReturn(java.util.Optional.of(layout));
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(false);
        when(contractRepository.save(contract)).thenReturn(contract);

        RentalContractResponse response = contractService.confirmDirectContract(tenantId, contractId);

        assertEquals(ContractStatus.SCHEDULED, contract.getStatus());
        assertEquals(Boolean.FALSE, response.isCanManageWms());
        verify(notificationService).push(
                eq(ownerId), any(), any(), eq("CONTRACT_RENEWAL_SCHEDULED"));
        verify(notificationService).push(
                eq(tenantId), any(), any(), eq("CONTRACT_RENEWAL_SCHEDULED"));
    }

    @Test
    void tenantCannotReviewRenewalAfterSourceDeadline() {
        RentalContract source = renewalSource();
        source.setEndDate(TODAY.minusDays(1));
        contract.setRenewedFromContract(source);
        when(contractRepository.findById(contractId)).thenReturn(java.util.Optional.of(contract));
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.findByIdForUpdate(source.getId())).thenReturn(java.util.Optional.of(source));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(java.util.Optional.of(contract));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> contractService.confirmDirectContract(tenantId, contractId));

        assertEquals(ErrorCode.CONTRACT_RENEWAL_DEADLINE_PASSED, exception.getErrorCode());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void rejectingRenewalDoesNotArchiveTheOperationalTenantLayout() {
        RentalContract source = renewalSource();
        contract.setRenewedFromContract(source);
        when(contractRepository.findById(contractId)).thenReturn(java.util.Optional.of(contract));
        when(contractRepository.save(contract)).thenReturn(contract);

        contractService.rejectDirectContract(tenantId, contractId, decision("Terms are incorrect"));

        assertEquals(ContractStatus.REJECTED, contract.getStatus());
        verify(warehouseLayoutService, never()).archiveTenantLayout(any(), any());
        verify(contractRepository, never()).existsCurrentDirectActiveContract(any(), any(), any());
        verify(notificationService).push(
                eq(ownerId), eq("Rental contract rejected"), any(), eq("CONTRACT_RENEWAL_REJECTED"));
    }

    @Test
    void tenantCanRequestChangesWithTrimmedReasonAndOwnerIsNotified() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        TenantContractDecisionRequest request = decision("  Please correct the leased area  ");

        contractService.requestDirectContractChanges(tenantId, contractId, request);

        assertEquals(ContractStatus.CHANGES_REQUESTED, contract.getStatus());
        assertEquals("Please correct the leased area", contract.getChangeRequestReason());
        verify(notificationService).push(
                eq(ownerId), eq("Rental contract changes requested"), any(), eq("CONTRACT_CHANGES_REQUESTED"));
        verifyNoInteractions(walletService);
    }

    @Test
    void renewalChangeRequestUsesRenewalNotificationType() {
        RentalContract source = renewalSource();
        contract.setRenewedFromContract(source);
        when(contractRepository.findById(contractId)).thenReturn(java.util.Optional.of(contract));
        when(contractRepository.save(contract)).thenReturn(contract);

        contractService.requestDirectContractChanges(
                tenantId, contractId, decision("Please update the renewal terms"));

        verify(notificationService).push(
                eq(ownerId), eq("Rental contract changes requested"), any(),
                eq("CONTRACT_RENEWAL_CHANGES_REQUESTED"));
    }

    @Test
    void tenantCanRejectDirectContractAndProposalIsArchivedWhenNoActiveContractExists() {
        stubContractLookup();
        when(contractRepository.save(contract)).thenReturn(contract);
        when(contractRepository.existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class)))
                .thenReturn(false);

        contractService.rejectDirectContract(tenantId, contractId, decision("Terms are incorrect"));

        assertEquals(ContractStatus.REJECTED, contract.getStatus());
        assertEquals("Terms are incorrect", contract.getRejectionReason());
        verify(warehouseLayoutService).archiveTenantLayout(warehouseId, tenantId);
        verifyNoInteractions(walletService);
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

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> contractService.requestDirectContractChanges(
                        tenantId, contractId, decision("Change the date")));

        assertEquals(ErrorCode.INVALID_CONTRACT_STATUS, exception.getErrorCode());
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

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> contractService.confirmDirectContract(tenantId, contractId));

        assertEquals(ErrorCode.CONTRACT_DATE_OVERLAP, exception.getErrorCode());
        assertEquals(ContractStatus.PENDING_TENANT_CONFIRM, contract.getStatus());
        verify(contractRepository, never()).save(any());
    }

    @Test
    void confirmationRejectsWhenAreaWasReservedAfterSubmission() {
        stubContractLookup();
        when(warehouseService.lockWarehouseForContractSubmit(warehouseId)).thenReturn(warehouse);
        when(contractRepository.existsDirectDateOverlapForSubmit(
                eq(contractId), eq(tenantId), eq(warehouseId),
                eq(contract.getStartDate()), eq(contract.getEndDate())))
                .thenReturn(false);
        when(warehouseRentalAvailabilityService.calculate(
                eq(warehouseId), eq(contractId), eq(contract.getStartDate()),
                eq(contract.getEndDate()), eq(contract.getLeasedAreaM2())))
                .thenReturn(RentalAreaAvailability.of(
                        new BigDecimal("200"), new BigDecimal("1"), new BigDecimal("200")));

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> contractService.confirmDirectContract(tenantId, contractId));

        assertEquals(ErrorCode.WAREHOUSE_AREA_UNAVAILABLE, exception.getErrorCode());
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

    private RentalContract renewalSource() {
        return RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.ACTIVE)
                .startDate(TODAY.minusDays(30))
                .endDate(TODAY)
                .pricingType(RentalPricingType.FIXED_MONTHLY)
                .finalMonthlyRent(new BigDecimal("1000000"))
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("20"))
                .leasedHeight(new BigDecimal("5"))
                .leasedAreaM2(new BigDecimal("200"))
                .layoutSnapshot("{}")
                .build();
    }

}
