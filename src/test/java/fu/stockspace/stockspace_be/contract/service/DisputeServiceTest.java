package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeTicketRepository disputeRepository;
    @Mock private RentalContractRepository contractRepository;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private ContractService contractService;
    @Mock private UserRepository userRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;

    @InjectMocks private DisputeService disputeService;

    @Test
    void getMyDisputes_includesDisputesWhereCurrentUserIsTheOtherContractParty() {
        UUID userId = UUID.randomUUID();
        Page<DisputeTicket> disputes = new PageImpl<>(List.of());
        when(disputeRepository.findByInvolvedUserId(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(disputes);

        var result = disputeService.getMyDisputes(userId, 0, 10);

        assertTrue(result.isEmpty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(disputeRepository).findByInvolvedUserId(eq(userId), pageableCaptor.capture());
        verify(disputeRepository, never()).findByRaisedById(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any(Pageable.class));
        assertTrue(pageableCaptor.getValue().getSort().isSorted());
    }
}
