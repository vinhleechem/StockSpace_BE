package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RentalContractSchemaMappingTest {

    @Test
    void contractUsesOnlyTheSixFinalStates() {
        assertEquals(Set.of(
                        "DRAFT", "PENDING_TENANT_CONFIRM", "CHANGES_REQUESTED",
                        "ACTIVE", "REJECTED", "EXPIRED"),
                Arrays.stream(ContractStatus.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void directRelationsAreRequired() throws Exception {
        for (String fieldName : Set.of("owner", "tenant", "warehouse")) {
            Field field = RentalContract.class.getDeclaredField(fieldName);
            assertFalse(field.getAnnotation(JoinColumn.class).nullable());
        }
    }

    @Test
    void warehouseUsesOnlyListingStatesAndCanonicalRentalPrice() throws Exception {
        assertEquals(Set.of("DRAFT", "AVAILABLE", "PENDING_APPROVAL", "INACTIVE"),
                Arrays.stream(WarehouseStatus.values()).map(Enum::name).collect(Collectors.toSet()));
        Warehouse.class.getDeclaredField("rentalPrice");
    }
}
