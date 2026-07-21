package fu.stockspace.stockspace_be.staff.repository;

import fu.stockspace.stockspace_be.staff.entity.InvitationStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {

    /**
     * Tìm lời mời theo token — dùng khi Staff click link xác nhận.
     */
    Optional<StaffInvitation> findByToken(String token);

    /**
     * Kiểm tra đã tồn tại lời mời PENDING cho email + tenant chưa.
     * Tránh gửi nhiều lời mời trùng.
     */
    boolean existsByEmailAndTenantIdAndStatus(String email, UUID tenantId, InvitationStatus status);
}
