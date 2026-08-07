package fu.stockspace.stockspace_be.wallet.repository;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByPaymentCode(String paymentCode);
    boolean existsByReferenceId(String referenceId);
    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);

    @Query("""
            SELECT EXTRACT(MONTH FROM t.createdAt) as month, SUM(t.amount) as total
            FROM Transaction t
            WHERE t.wallet.id = :walletId
              AND t.type = :type
              AND EXTRACT(YEAR FROM t.createdAt) = :year
            GROUP BY EXTRACT(MONTH FROM t.createdAt)
            """)
    List<Object[]> findMonthlyRevenueByWalletIdAndTypeAndYear(
            @Param("walletId") UUID walletId,
            @Param("type") fu.stockspace.stockspace_be.wallet.entity.TransactionType type,
            @Param("year") int year
    );

    @Query("""
            SELECT EXTRACT(MONTH FROM t.createdAt) as month, SUM(t.amount) as total
            FROM Transaction t
            WHERE t.type = :type
              AND EXTRACT(YEAR FROM t.createdAt) = :year
            GROUP BY EXTRACT(MONTH FROM t.createdAt)
            """)
    List<Object[]> findMonthlyRevenueByTypeAndYear(
            @Param("type") fu.stockspace.stockspace_be.wallet.entity.TransactionType type,
            @Param("year") int year
    );
}