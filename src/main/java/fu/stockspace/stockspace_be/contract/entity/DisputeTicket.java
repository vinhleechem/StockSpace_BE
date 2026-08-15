package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


import java.util.UUID;









@Entity
@Table(name = "dispute_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DisputeTicket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


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


    @Column(name = "evidence_images", columnDefinition = "TEXT")
    private String evidenceImages;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";


    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;
}
