package fu.stockspace.stockspace_be.wallet.repository;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByPaymentCode(String paymentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.paymentCode = :paymentCode")
    Optional<Transaction> findByPaymentCodeForUpdate(@Param("paymentCode") String paymentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.transactionType = fu.stockspace.stockspace_be.wallet.entity.TransactionType.TOP_UP
              AND t.status = fu.stockspace.stockspace_be.wallet.entity.TransactionStatus.PENDING
              AND (
                    (t.expiresAt IS NOT NULL AND t.expiresAt <= :expiryCutoff)
                    OR (t.expiresAt IS NULL AND t.createdAt IS NOT NULL AND t.createdAt <= :createdCutoff)
              )
            ORDER BY COALESCE(t.expiresAt, t.createdAt) ASC
            """)
    List<Transaction> findExpiredPendingTopUpsForUpdate(
            @Param("expiryCutoff") LocalDateTime expiryCutoff,
            @Param("createdCutoff") LocalDateTime createdCutoff
    );
    Optional<Transaction> findByListingOrderIdAndTransactionType(UUID listingOrderId, TransactionType transactionType);
    List<Transaction> findAllByListingOrderIdInAndTransactionType(
            List<UUID> listingOrderIds,
            TransactionType transactionType
    );
    boolean existsByReferenceId(String referenceId);
    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);

    @Query("""
            SELECT EXTRACT(MONTH FROM t.createdAt) as month, SUM(t.amount) as total
            FROM Transaction t
            WHERE t.wallet.id = :walletId
              AND t.transactionType = :type
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
            WHERE t.wallet.id = :walletId
              AND t.transactionType IN :types
              AND EXTRACT(YEAR FROM t.createdAt) = :year
            GROUP BY EXTRACT(MONTH FROM t.createdAt)
            """)
    List<Object[]> findMonthlyRevenueByWalletIdAndTypesAndYear(
            @Param("walletId") UUID walletId,
            @Param("types") List<fu.stockspace.stockspace_be.wallet.entity.TransactionType> types,
            @Param("year") int year
    );

    @Query("""
            SELECT EXTRACT(MONTH FROM t.createdAt) as month, SUM(t.amount) as total
            FROM Transaction t
            WHERE t.transactionType = :type
              AND EXTRACT(YEAR FROM t.createdAt) = :year
            GROUP BY EXTRACT(MONTH FROM t.createdAt)
            """)
    List<Object[]> findMonthlyRevenueByTypeAndYear(
            @Param("type") fu.stockspace.stockspace_be.wallet.entity.TransactionType type,
            @Param("year") int year
    );

    @Query("""
            SELECT EXTRACT(MONTH FROM t.createdAt) as month, SUM(t.amount) as total
            FROM Transaction t
            WHERE t.transactionType IN :types
              AND EXTRACT(YEAR FROM t.createdAt) = :year
            GROUP BY EXTRACT(MONTH FROM t.createdAt)
            """)
    List<Object[]> findMonthlyRevenueByTypesAndYear(
            @Param("types") List<fu.stockspace.stockspace_be.wallet.entity.TransactionType> types,
            @Param("year") int year
    );
}
