package fu.stockspace.stockspace_be.contract.repository;

import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


import java.util.UUID;

public interface DisputeTicketRepository extends JpaRepository<DisputeTicket, UUID> {

    Optional<DisputeTicket> findByContractId(UUID contractId);

    Page<DisputeTicket> findByRaisedById(UUID userId, Pageable pageable);

    Page<DisputeTicket> findByStatus(String status, Pageable pageable);

    @Query("SELECT d FROM DisputeTicket d WHERE d.status = :status OR :status IS NULL")
    Page<DisputeTicket> findAllByStatusOptional(@Param("status") String status, Pageable pageable);
}
