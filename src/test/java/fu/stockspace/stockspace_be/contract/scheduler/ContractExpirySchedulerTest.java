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
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractExpirySchedulerTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private WarehouseLayoutService warehouseLayoutService;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks private ContractExpiryScheduler scheduler;

    private User tenant;
    private User owner;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(UUID.randomUUID()).email("tenant@test.com").fullName("Tenant").build();
        owner = User.builder().id(UUID.randomUUID()).email("owner@test.com").fullName("Owner").build();
        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Warehouse A").owner(owner).build();
    }

    @Test
    void expiryClearsOnlyTenantStockArchivesLayoutAndRevokesAssignments() {
        RentalContract contract = activeContract(LocalDate.now().minusDays(1));
        StockBatch batch = StockBatch.builder().quantity(5).isActive(true).isDeleted(false).build();
        StaffWarehouseAssignment assignment = StaffWarehouseAssignment.builder()
                .status(AssignmentStatus.ACTIVE).startDate(LocalDateTime.now().minusDays(5)).build();
        stubDailyQueries(List.of(), List.of(contract));
        when(contractRepository.existsOtherCurrentDirectActiveContract(
                eq(contract.getId()), eq(tenant.getId()), eq(warehouse.getId()), any())).thenReturn(false);
        when(stockBatchRepository.findAllByWarehouseIdAndTenantId(warehouse.getId(), tenant.getId()))
                .thenReturn(List.of(batch));
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(
                tenant.getId(), warehouse.getId(), AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));

        scheduler.expireContracts();

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        assertTrue(batch.isDeleted());
        assertFalse(batch.isActive());
        assertEquals(AssignmentStatus.REVOKED, assignment.getStatus());
        assertFalse(assignment.isActive());
        verify(stockBatchRepository).findAllByWarehouseIdAndTenantId(warehouse.getId(), tenant.getId());
        verify(warehouseLayoutService).archiveTenantLayout(warehouse.getId(), tenant.getId());
        verify(assignmentRepository).saveAll(List.of(assignment));
        verify(notificationService, times(2)).push(any(), eq("Rental contract expired"), any(), eq("CONTRACT_EXPIRED"));
        verify(contractRepository).save(contract);
    }

    @Test
    void reminderUsesExactThirtyDayBoundaryAndIsMarkedOnce() {
        LocalDate today = LocalDate.now();
        RentalContract contract = activeContract(today.plusDays(30));
        when(contractRepository.findActiveContractsEndingBetween(today.plusDays(30), today.plusDays(30)))
                .thenReturn(List.of(contract));
        when(contractRepository.findActiveContractsEndingBefore(today)).thenReturn(List.of());

        scheduler.expireContracts();

        assertTrue(contract.isExpiryReminderSent());
        verify(emailService, times(2)).sendContractExpiryReminderEmail(
                any(), any(), eq(warehouse.getName()), eq(contract.getEndDate()));
        verify(notificationService, times(2)).push(
                any(), eq("Warehouse contract expiry reminder"), any(), eq("CONTRACT_EXPIRY_REMINDER"));
        verify(contractRepository).save(contract);
    }

    @Test
    void activeSiblingMakesExpiryRetainSharedOperationalData() {
        RentalContract contract = activeContract(LocalDate.now().minusDays(1));
        stubDailyQueries(List.of(), List.of(contract));
        when(contractRepository.existsOtherCurrentDirectActiveContract(
                eq(contract.getId()), eq(tenant.getId()), eq(warehouse.getId()), any())).thenReturn(true);

        scheduler.expireContracts();

        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        verifyNoInteractions(stockBatchRepository, warehouseLayoutService, assignmentRepository);
        verify(contractRepository).save(contract);
    }

    @Test
    void repeatedRunIsIdempotentAndDoesNotCleanOrNotifyTwice() {
        RentalContract contract = activeContract(LocalDate.now().minusDays(1));
        when(contractRepository.findActiveContractsEndingBetween(any(), any())).thenReturn(List.of());
        when(contractRepository.findActiveContractsEndingBefore(any())).thenReturn(List.of(contract));
        when(contractRepository.existsOtherCurrentDirectActiveContract(any(), any(), any(), any())).thenReturn(false);
        when(stockBatchRepository.findAllByWarehouseIdAndTenantId(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(any(), any(), any())).thenReturn(List.of());

        scheduler.expireContracts();
        scheduler.expireContracts();

        verify(contractRepository, times(1)).save(contract);
        verify(warehouseLayoutService, times(1)).archiveTenantLayout(warehouse.getId(), tenant.getId());
        verify(notificationService, times(2)).push(any(), any(), any(), eq("CONTRACT_EXPIRED"));
    }

    @Test
    void notificationFailureDoesNotRollBackExpiryState() {
        RentalContract contract = activeContract(LocalDate.now().minusDays(1));
        stubDailyQueries(List.of(), List.of(contract));
        when(contractRepository.existsOtherCurrentDirectActiveContract(any(), any(), any(), any())).thenReturn(false);
        when(stockBatchRepository.findAllByWarehouseIdAndTenantId(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(any(), any(), any())).thenReturn(List.of());
        doThrow(new RuntimeException("websocket unavailable"))
                .when(notificationService).push(any(), any(), any(), eq("CONTRACT_EXPIRED"));

        assertDoesNotThrow(() -> scheduler.expireContracts());
        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        verify(contractRepository).save(contract);
    }

    private RentalContract activeContract(LocalDate endDate) {
        return RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(endDate)
                .isActive(true)
                .isDeleted(false)
                .build();
    }

    private void stubDailyQueries(List<RentalContract> reminders, List<RentalContract> expired) {
        when(contractRepository.findActiveContractsEndingBetween(any(), any())).thenReturn(reminders);
        when(contractRepository.findActiveContractsEndingBefore(any())).thenReturn(expired);
    }
}
