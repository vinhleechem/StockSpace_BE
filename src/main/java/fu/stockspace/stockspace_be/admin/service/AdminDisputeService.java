package fu.stockspace.stockspace_be.admin.service;

import fu.stockspace.stockspace_be.admin.dto.ResolveDisputeRequest;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.service.DisputeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDisputeService {

    private final DisputeTicketRepository disputeRepository;
    private final DisputeService disputeService;




    @Transactional(readOnly = true)
    public Page<DisputeResponse> getAllDisputes(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String queryStatus = (status == null || status.trim().isEmpty()) ? null : status.trim().toUpperCase();

        return disputeRepository.findAllByStatusOptional(queryStatus, pageable)
                .map(disputeService::mapToResponse);
    }




    @Transactional
    public DisputeResponse resolveDispute(java.util.UUID disputeId, java.util.UUID adminId, ResolveDisputeRequest request) {
        log.info("Admin {} resolving dispute {} with decision {}", adminId, disputeId, request.getDepositResolution());
        return disputeService.resolveDispute(disputeId, adminId, request.getAdminNote(), request.getDepositResolution());
    }
}
