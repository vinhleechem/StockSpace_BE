package fu.stockspace.stockspace_be.wallet.entity;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;
/**
 * Entity WithdrawRequest - Yêu cầu rút tiền từ ví của người dùng.
 */
@Entity
@Table(name = "withdraw_requests", indexes = {
        @Index(name = "idx_withdraw_requests_user_id", columnList = "user_id"),
        @Index(name = "idx_withdraw_requests_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WithdrawRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    /** Liên kết 1-1 tới giao dịch hoàn tất (nullable khi chưa duyệt) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;
    @Column(name = "bank_account_number", nullable = false, length = 50)
    private String bankAccountNumber;
    @Column(name = "bank_account_holder", nullable = false, length = 150)
    private String bankAccountHolder;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;
    /** Ghi chú lý do từ chối hoặc duyệt của Admin */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;
}