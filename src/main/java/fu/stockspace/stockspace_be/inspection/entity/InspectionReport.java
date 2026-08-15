package fu.stockspace.stockspace_be.inspection.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;










@Entity
@Table(name = "inspection_reports", indexes = {
        @Index(name = "idx_inspection_reports_warehouse_id",  columnList = "warehouse_id"),
        @Index(name = "idx_inspection_reports_inspector_id",  columnList = "inspector_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InspectionReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspector_id")
    private User inspector;





    @Column(name = "checklist_data", columnDefinition = "TEXT")
    private String checklistData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InspectionStatus status = InspectionStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ElementCollection
    @CollectionTable(name = "inspection_report_images", joinColumns = @JoinColumn(name = "inspection_report_id"))
    @Column(name = "image_url", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;
}
