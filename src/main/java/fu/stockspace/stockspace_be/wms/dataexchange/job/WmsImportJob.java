package fu.stockspace.stockspace_be.wms.dataexchange.job;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wms_import_jobs", indexes = {
        @Index(name = "idx_wms_import_jobs_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_wms_import_jobs_created_by_created", columnList = "created_by, created_at"),
        @Index(name = "idx_wms_import_jobs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WmsImportJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id")
    private InventoryAudit audit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 40, updatable = false)
    private WmsImportType importType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WmsImportJobStatus status;

    @Column(name = "schema_version", nullable = false, length = 20, updatable = false)
    private String schemaVersion;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "file_sha256", nullable = false, length = 64, updatable = false)
    private String fileSha256;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_metadata", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> contextMetadata = new LinkedHashMap<>();

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private int validRows = 0;

    @Column(name = "invalid_rows", nullable = false)
    @Builder.Default
    private int invalidRows = 0;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
