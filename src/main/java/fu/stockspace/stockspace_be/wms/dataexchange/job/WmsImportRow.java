package fu.stockspace.stockspace_be.wms.dataexchange.job;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "wms_import_rows", uniqueConstraints = {
        @UniqueConstraint(name = "ux_wms_import_rows_job_sheet_row", columnNames = {"job_id", "sheet_name", "row_number"})
}, indexes = {
        @Index(name = "idx_wms_import_rows_job_group", columnList = "job_id, group_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WmsImportRow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private WmsImportJob job;

    @Column(name = "sheet_name", nullable = false, length = 80)
    private String sheetName;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "group_key", length = 120)
    private String groupKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> normalizedPayload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, String>> validationErrors = new ArrayList<>();

    @Column(name = "result_resource_type", length = 40)
    private String resultResourceType;

    @Column(name = "result_resource_id")
    private UUID resultResourceId;
}
