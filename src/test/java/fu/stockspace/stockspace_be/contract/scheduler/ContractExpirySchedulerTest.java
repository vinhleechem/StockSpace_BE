package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractExpirySchedulerTest {

    @Mock
    private RentalContractRepository contractRepository;
    @Mock
    private WarehouseService warehouseService;
    @Mock
    private WarehouseLayoutService warehouseLayoutService;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private ContractExpiryScheduler scheduler;

    @Test
    void expireContracts_ClearsStockArchivesLayoutAndRevokesAssignments() {
        User tenant = User.builder().id(java.util.UUID.randomUUID()).email("tenant@test.com").build();
        User owner = User.builder().id(java.util.UUID.randomUUID()).email("owner@test.com").build();
        Warehouse warehouse = Warehouse.builder()
                .id(java.util.UUID.randomUUID())
                .name("Warehouse A")
                .owner(owner)
                .build();
        BookingRequest booking = BookingRequest.builder()
                .tenant(tenant)
                .warehouse(warehouse)
                .build();
        RentalContract contract = RentalContract.builder()
                .id(java.util.UUID.randomUUID())
                .booking(booking)
                .status(ContractStatus.ACTIVE)
                .endDate(LocalDate.now().minusDays(1))
                .build();

        StockBatch batch = StockBatch.builder().quantity(5).build();
        StaffWarehouseAssignment assignment = StaffWarehouseAssignment.builder()
                .status(AssignmentStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(5))
                .build();

        when(systemConfigService.getIntValue("contract_expiry_days", 7)).thenReturn(7);
        when(contractRepository.findByStatusAndSubmittedAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findActiveContractsEndingBetween(any(), any()))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findActiveContractsEndingBefore(any()))
                .thenReturn(List.of(contract));
        when(stockBatchRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouse.getId()))
                .thenReturn(List.of(batch));
        when(assignmentRepository.findByTenantIdAndWarehouseIdAndStatus(
                tenant.getId(), warehouse.getId(), AssignmentStatus.ACTIVE))
                .thenReturn(List.of(assignment));

        scheduler.expireContracts();

        assertTrue(batch.isDeleted());
        assertFalse(batch.isActive());
        assertEquals(AssignmentStatus.REVOKED, assignment.getStatus());
        assertEquals(ContractStatus.COMPLETED, contract.getStatus());
        verify(stockBatchRepository).saveAll(List.of(batch));
        verify(warehouseLayoutService).archiveTenantLayout(warehouse.getId(), tenant.getId());
        verify(assignmentRepository).saveAll(List.of(assignment));
        verify(warehouseService).markAsAvailable(warehouse.getId());
        verify(contractRepository).save(contract);
    }

    @Test
    void expireContracts_SendsReminderOnceForContractsNearExpiry() {
        User tenant = User.builder().id(java.util.UUID.randomUUID()).email("tenant@test.com").fullName("Tenant").build();
        User owner = User.builder().id(java.util.UUID.randomUUID()).email("owner@test.com").fullName("Owner").build();
        Warehouse warehouse = Warehouse.builder()
                .id(java.util.UUID.randomUUID())
                .name("Warehouse A")
                .owner(owner)
                .build();
        BookingRequest booking = BookingRequest.builder().tenant(tenant).warehouse(warehouse).build();
        RentalContract contract = RentalContract.builder()
                .id(java.util.UUID.randomUUID())
                .booking(booking)
                .status(ContractStatus.ACTIVE)
                .endDate(LocalDate.now().plusDays(30))
                .build();

        when(systemConfigService.getIntValue("contract_expiry_days", 7)).thenReturn(7);
        when(contractRepository.findActiveContractsEndingBetween(any(), any()))
                .thenReturn(List.of(contract));
        when(contractRepository.findByStatusAndSubmittedAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(contractRepository.findActiveContractsEndingBefore(any()))
                .thenReturn(Collections.emptyList());

        scheduler.expireContracts();

        assertTrue(contract.isExpiryReminderSent());
        verify(emailService, times(2)).sendContractExpiryReminderEmail(
                any(), any(), eq("Warehouse A"), eq(contract.getEndDate()));
        verify(notificationService, times(2)).push(any(), eq("Warehouse contract expiry reminder"), any(), eq("RENTAL"));
        verify(contractRepository).save(contract);
    }
}
