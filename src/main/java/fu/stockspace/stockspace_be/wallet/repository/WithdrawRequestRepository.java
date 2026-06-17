package fu.stockspace.stockspace_be.wallet.repository;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wallet.entity.WithdrawRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, UUID> {
    Page<WithdrawRequest> findByUserId(Long userId, Pageable pageable);
    Page<WithdrawRequest> findByStatus(ApprovalStatus status, Pageable pageable);
}