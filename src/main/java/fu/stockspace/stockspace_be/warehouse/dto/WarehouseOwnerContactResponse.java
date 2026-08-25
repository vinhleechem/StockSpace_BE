package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Contact information returned only to an authenticated user who requests to
 * contact a warehouse owner.
 */
@Getter
@Builder
public class WarehouseOwnerContactResponse {

    private UUID warehouseId;
    private UUID ownerId;
    private String ownerName;
    private String phone;
}
