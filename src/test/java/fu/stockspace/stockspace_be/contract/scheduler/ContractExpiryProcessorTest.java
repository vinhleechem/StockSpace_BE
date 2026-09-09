package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractExpiryProcessorTest {

    @Mock
    private RentalContractRepository contractRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseLayoutService warehouseLayoutService;

    @Mock
    private StockBatchRepository stockBatchRepository;

    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    private ContractExpiryProcessor processor;
    private User tenant;
    private User owner;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        tenant = User.builder()
                .id(UUID.randomUUID())
                .email("tenant@test.com")
                .fullName("Tenant")
                .build();
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@test.com")
                .fullName("Owner")
                .build();
        warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Warehouse A")
                .owner(owner)
                .build();
        processor = new ContractExpiryProcessor(
                contractRepository,
                warehouseRepository,
                warehouseLayoutService,
                stockBatchRepository,
                assignmentRepository,
                notificationService,
                emailService);
    }

    @Test
    void expiryClearsOnlyTenantStockArchivesLayoutAndRevokesAssignments() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = activeContract(today.minusDays(1));
        StockBatch batch = StockBatch.builder()
                .quantity(5)
                .isActive(true)
                .isDeleted(false)
                .build();
        StaffWarehouseAssignment assignment = StaffWarehouseAssignment.builder()
                .status(AssignmentStatus.ACTIVE)
                .startDate(LocalDateTime.now(ContractExpiryScheduler.BUSINESS_ZONE).minusDays(5))
                .build();
        stubLockedContract(contract);
        when(contractRepository.existsOtherCurrentDirectActiveContract(
                contract.getId(), tenant.getId(), warehouse.getId(), today)).thenReturn(false);
        when(stockBatchRepository.findAllByWarehouseIdAndTenantId(warehouse.getId(), tenant.getId()))
                .thenReturn(List.of(batch));
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(
                tenant.getId(), warehouse.getId(), AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));

        processor.expireActiveContract(contract.getId(), today);

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        assertTrue(batch.isDeleted());
        assertFalse(batch.isActive());
        assertEquals(AssignmentStatus.REVOKED, assignment.getStatus());
        assertFalse(assignment.isActive());
        assertNotNull(assignment.getEndDate());
        verify(stockBatchRepository).findAllByWarehouseIdAndTenantId(warehouse.getId(), tenant.getId());
        verify(warehouseLayoutService).archiveTenantLayout(warehouse.getId(), tenant.getId());
        verify(assignmentRepository).saveAll(List.of(assignment));
        verify(notificationService, times(2)).push(
                any(), eq("Rental contract expired"), any(), eq("CONTRACT_EXPIRED"));
        verify(contractRepository).save(contract);
    }

    @Test
    void expiryRetainsSharedOperationalDataWhenAnActiveSiblingExists() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = activeContract(today.minusDays(1));
        stubLockedContract(contract);
        when(contractRepository.existsOtherCurrentDirectActiveContract(
                contract.getId(), tenant.getId(), warehouse.getId(), today)).thenReturn(true);

        processor.expireActiveContract(contract.getId(), today);

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        verifyNoInteractions(stockBatchRepository, warehouseLayoutService, assignmentRepository);
        verify(contractRepository).save(contract);
    }

    @Test
    void reminderUsesTheThirtyDayWindowAndMarksItSentAfterTenantNotification() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = activeContract(today.plusDays(7));
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));

        processor.sendExpiryReminder(contract.getId(), today);

        assertTrue(contract.isExpiryReminderSent());
        verify(emailService, times(2)).sendContractExpiryReminderEmail(
                any(), any(), eq(warehouse.getName()), eq(contract.getEndDate()));
        verify(notificationService).push(
                eq(tenant.getId()), eq("Warehouse contract expiry reminder"), any(),
                eq("CONTRACT_EXPIRY_REMINDER"));
        verify(notificationService).push(
                eq(owner.getId()), eq("Warehouse contract expiry reminder"), any(),
                eq("CONTRACT_EXPIRY_REMINDER"));
        verify(contractRepository).save(contract);
    }

    @Test
    void reminderSkipsSourceWhenRenewalIsAlreadyScheduled() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract source = activeContract(today.plusDays(7));
        RentalContract renewal = scheduledContract(source.getEndDate().plusDays(1));
        renewal.setRenewedFromContract(source);
        when(contractRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(contractRepository.findBlockingRenewalsBySourceId(source.getId()))
                .thenReturn(List.of(renewal));

        processor.sendExpiryReminder(source.getId(), today);

        verifyNoInteractions(emailService, notificationService);
        verify(contractRepository, never()).save(source);
    }

    @Test
    void failedTenantReminderRemainsPendingForRetry() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = activeContract(today.plusDays(7));
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationService).push(eq(tenant.getId()),
                        eq("Warehouse contract expiry reminder"), any(),
                        eq("CONTRACT_EXPIRY_REMINDER"));

        processor.sendExpiryReminder(contract.getId(), today);

        assertFalse(contract.isExpiryReminderSent());
        verify(contractRepository, never()).save(contract);
        verify(notificationService).push(eq(owner.getId()),
                eq("Warehouse contract expiry reminder"), any(),
                eq("CONTRACT_EXPIRY_REMINDER"));
    }

    @Test
    void ordinaryScheduledContractBecomesActiveOnItsStartDate() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = scheduledContract(today);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(warehouseRepository.findByIdForUpdate(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));

        processor.activateScheduledContract(contract.getId(), today);

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        verify(contractRepository).save(contract);
    }

    @Test
    void renewalHandoverActivatesSuccessorAndExpiresSourceWithoutOperationalCleanup() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract source = activeContract(today.minusDays(1));
        RentalContract successor = scheduledContract(today);
        successor.setRenewedFromContract(source);
        successor.setEndDate(today.plusMonths(1));

        when(contractRepository.findById(successor.getId())).thenReturn(Optional.of(successor));
        when(warehouseRepository.findByIdForUpdate(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(contractRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(contractRepository.findByIdForUpdate(successor.getId())).thenReturn(Optional.of(successor));

        processor.activateScheduledContract(successor.getId(), today);

        assertEquals(ContractStatus.ACTIVE, successor.getStatus());
        assertEquals(ContractStatus.EXPIRED, source.getStatus());
        verify(contractRepository).save(successor);
        verify(contractRepository).save(source);
        verifyNoInteractions(stockBatchRepository, warehouseLayoutService, assignmentRepository,
                emailService);
        verify(notificationService, times(2)).push(
                any(), eq("Rental contract renewal activated"), any(),
                eq("CONTRACT_RENEWAL_ACTIVATED"));
    }

    @Test
    void renewalActivationNotificationFailureDoesNotUndoHandover() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract source = activeContract(today.minusDays(1));
        RentalContract successor = scheduledContract(today);
        successor.setRenewedFromContract(source);
        successor.setEndDate(today.plusMonths(1));

        when(contractRepository.findById(successor.getId())).thenReturn(Optional.of(successor));
        when(warehouseRepository.findByIdForUpdate(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(contractRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(contractRepository.findByIdForUpdate(successor.getId())).thenReturn(Optional.of(successor));
        doThrow(new RuntimeException("websocket unavailable"))
                .when(notificationService).push(any(), eq("Rental contract renewal activated"), any(),
                        eq("CONTRACT_RENEWAL_ACTIVATED"));

        assertDoesNotThrow(() -> processor.activateScheduledContract(successor.getId(), today));

        assertEquals(ContractStatus.ACTIVE, successor.getStatus());
        assertEquals(ContractStatus.EXPIRED, source.getStatus());
        verify(contractRepository).save(successor);
        verify(contractRepository).save(source);
    }

    @Test
    void repeatedScheduledActivationIsIdempotent() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = scheduledContract(today);
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(warehouseRepository.findByIdForUpdate(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));

        processor.activateScheduledContract(contract.getId(), today);
        processor.activateScheduledContract(contract.getId(), today);

        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        verify(contractRepository, times(1)).save(contract);
    }

    @Test
    void notificationFailureDoesNotUndoExpiryTransition() {
        LocalDate today = LocalDate.now(ContractExpiryScheduler.BUSINESS_ZONE);
        RentalContract contract = activeContract(today.minusDays(1));
        stubLockedContract(contract);
        when(contractRepository.existsOtherCurrentDirectActiveContract(
                any(), any(), any(), eq(today))).thenReturn(false);
        when(stockBatchRepository.findAllByWarehouseIdAndTenantId(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(any(), any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("websocket unavailable"))
                .when(notificationService).push(any(), any(), any(), eq("CONTRACT_EXPIRED"));

        assertDoesNotThrow(() -> processor.expireActiveContract(contract.getId(), today));

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        verify(contractRepository).save(contract);
    }

    private void stubLockedContract(RentalContract contract) {
        when(contractRepository.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(warehouseRepository.findByIdForUpdate(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(contractRepository.findByIdForUpdate(contract.getId())).thenReturn(Optional.of(contract));
    }

    private RentalContract activeContract(LocalDate endDate) {
        return RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.ACTIVE)
                .startDate(endDate.minusMonths(1))
                .endDate(endDate)
                .isActive(true)
                .isDeleted(false)
                .build();
    }

    private RentalContract scheduledContract(LocalDate startDate) {
        return RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.SCHEDULED)
                .startDate(startDate)
                .endDate(startDate.plusMonths(1))
                .isActive(true)
                .isDeleted(false)
                .build();
    }
}
