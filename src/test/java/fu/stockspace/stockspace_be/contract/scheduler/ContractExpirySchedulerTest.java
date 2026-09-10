package fu.stockspace.stockspace_be.contract.scheduler;

import fu.stockspace.stockspace_be.common.config.BusinessTimeConfig;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractExpirySchedulerTest {

    @Mock
    private RentalContractRepository contractRepository;

    @Mock
    private ContractExpiryProcessor contractExpiryProcessor;

    @Mock
    private Clock businessClock;

    @BeforeEach
    void setUp() {
        lenient().when(businessClock.instant())
                .thenReturn(Instant.parse("2026-09-09T17:00:00Z"));
        lenient().when(businessClock.getZone())
                .thenReturn(BusinessTimeConfig.BUSINESS_ZONE_ID);
    }

    @Test
    void processesRemindersBeforeActivationsAndExpiries() {
        LocalDate today = LocalDate.now(businessClock);
        UUID reminderId = UUID.randomUUID();
        UUID scheduledId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        when(contractRepository.findActiveContractIdsEndingBetween(today, today.plusDays(30)))
                .thenReturn(List.of(reminderId));
        when(contractRepository.findScheduledContractIdsDueOnOrBefore(today))
                .thenReturn(List.of(scheduledId));
        when(contractRepository.findActiveContractIdsEndingBefore(today))
                .thenReturn(List.of(expiredId));

        ContractExpiryScheduler scheduler = new ContractExpiryScheduler(
                contractRepository, contractExpiryProcessor, businessClock);

        scheduler.expireContracts();

        InOrder inOrder = inOrder(contractRepository, contractExpiryProcessor);
        inOrder.verify(contractRepository).findActiveContractIdsEndingBetween(
                today, today.plusDays(30));
        inOrder.verify(contractExpiryProcessor).sendExpiryReminder(reminderId, today);
        inOrder.verify(contractRepository).findScheduledContractIdsDueOnOrBefore(today);
        inOrder.verify(contractExpiryProcessor).activateScheduledContract(scheduledId, today);
        inOrder.verify(contractRepository).findActiveContractIdsEndingBefore(today);
        inOrder.verify(contractExpiryProcessor).expireActiveContract(expiredId, today);
        verifyNoMoreInteractions(contractExpiryProcessor);
    }

    @Test
    void oneCandidateFailureDoesNotStopTheRemainingCandidates() {
        LocalDate today = LocalDate.now(businessClock);
        UUID failedReminderId = UUID.randomUUID();
        UUID successfulReminderId = UUID.randomUUID();
        UUID scheduledId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        when(contractRepository.findActiveContractIdsEndingBetween(any(), any()))
                .thenReturn(List.of(failedReminderId, successfulReminderId));
        when(contractRepository.findScheduledContractIdsDueOnOrBefore(any()))
                .thenReturn(List.of(scheduledId));
        when(contractRepository.findActiveContractIdsEndingBefore(any()))
                .thenReturn(List.of(expiredId));
        doThrow(new IllegalStateException("candidate failed"))
                .when(contractExpiryProcessor).sendExpiryReminder(failedReminderId, today);

        ContractExpiryScheduler scheduler = new ContractExpiryScheduler(
                contractRepository, contractExpiryProcessor, businessClock);

        assertDoesNotThrow(scheduler::expireContracts);

        verify(contractExpiryProcessor).sendExpiryReminder(failedReminderId, today);
        verify(contractExpiryProcessor).sendExpiryReminder(successfulReminderId, today);
        verify(contractExpiryProcessor).activateScheduledContract(scheduledId, today);
        verify(contractExpiryProcessor).expireActiveContract(expiredId, today);
    }
}
