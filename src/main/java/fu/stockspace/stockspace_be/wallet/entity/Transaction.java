package fu.stockspace.stockspace_be.wallet.entity;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;
/**
 * Entity Transaction - Lưu lịch sử biến động số dư và giao dịch.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_transactions_payment_code", columnList = "payment_code"),
        @Index(name = "idx_transactions_reference_id", columnList = "reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Transaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;
    /** Mã chuyển khoản duy nhất (ví dụ: STSPX7F2A) */
    @Column(name = "payment_code", unique = true, length = 50)
    private String paymentCode;
    /** ID giao dịch đối tác / SePay phục vụ đối soát và tránh trùng lặp */
    @Column(name = "reference_id", unique = true, length = 100)
    private String referenceId;
    /** ID của booking liên quan (nếu có) - Loose coupling kiểu Long */
    @Column(name = "booking_id")
    private Long bookingId;
    /** ID của subscription liên quan (nếu có) - Loose coupling kiểu UUID */
    @Column(name = "subscription_id")
    private UUID subscriptionId;
}