package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


/**
 * Entity DisputeTicket — tranh chấp được mở bởi một bên (Owner hoặc Tenant).
 * Map với bảng: dispute_tickets
 *
 * Khi dispute được tạo → RentalContract.status = DISPUTED.
 * Admin giải quyết → status = RESOLVED, Admin ghi note.
 */
@Entity
@Table(name = "dispute_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DisputeTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** 1-1 với RentalContract */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", unique = true, nullable = false)
    private RentalContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by", nullable = false)
    private User raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** JSON array URL ảnh bằng chứng */
    @Column(name = "evidence_images", columnDefinition = "TEXT")
    private String evidenceImages;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    /** Ghi chú của Admin khi giải quyết */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
