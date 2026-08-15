package fu.stockspace.stockspace_be.staff.repository;

import fu.stockspace.stockspace_be.staff.entity.InvitationStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {




    Optional<StaffInvitation> findByToken(String token);





    boolean existsByEmailAndTenantIdAndStatus(String email, UUID tenantId, InvitationStatus status);




    long countByTenantIdAndStatus(UUID tenantId, InvitationStatus status);
}
