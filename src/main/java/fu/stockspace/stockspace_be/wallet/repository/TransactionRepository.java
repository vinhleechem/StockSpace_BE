package fu.stockspace.stockspace_be.wallet.repository;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByPaymentCode(String paymentCode);
    boolean existsByReferenceId(String referenceId);
    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);
}