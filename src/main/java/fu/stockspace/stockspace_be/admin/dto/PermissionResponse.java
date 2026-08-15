package fu.stockspace.stockspace_be.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;




@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {

    private java.util.UUID id;
    private String name;
    private String description;
}
