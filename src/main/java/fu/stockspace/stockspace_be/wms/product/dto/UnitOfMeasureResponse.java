package fu.stockspace.stockspace_be.wms.product.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitOfMeasureResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
}
