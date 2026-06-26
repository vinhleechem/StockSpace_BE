package fu.stockspace.stockspace_be.common.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigResponse {
    private UUID id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
