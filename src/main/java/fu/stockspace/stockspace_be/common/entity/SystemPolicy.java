package fu.stockspace.stockspace_be.common.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;





@Entity
@Table(name = "system_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SystemPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "version", unique = true, nullable = false, length = 50)
    private String version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
