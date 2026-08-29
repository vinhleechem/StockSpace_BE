package fu.stockspace.stockspace_be.warehouse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Contact information returned only to an authenticated user who requests to
 * contact a warehouse owner.
 */
@Getter
@Builder
@Schema(description = "Owner contact returned only by the authenticated owner-contact endpoint")
public class WarehouseOwnerContactResponse {

    private UUID warehouseId;
    private UUID ownerId;
    private String ownerName;
    @Schema(example = "+84901234567")
    private String phone;
}
