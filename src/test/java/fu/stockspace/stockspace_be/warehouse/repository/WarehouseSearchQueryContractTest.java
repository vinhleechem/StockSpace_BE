package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseSearchQueryContractTest {

    @Test
    void publicSearchRetainsVisibilityAndAllStructuredFilterPredicates() throws Exception {
        Method searchMethod = WarehouseRepository.class.getMethod(
                "searchPublic",
                String.class,
                fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                java.math.BigDecimal.class,
                String.class,
                String.class,
                java.util.UUID.class,
                Boolean.class,
                org.springframework.data.domain.Pageable.class
        );
        String query = searchMethod.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

        assertTrue(query.contains("w.isActive = true"));
        assertTrue(query.contains("w.isDeleted = false"));
        assertTrue(query.contains("w.isVerified = true"));
        assertTrue(query.contains("w.status = fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.AVAILABLE"));
        assertTrue(query.contains("w.publishedAt IS NOT NULL"));
        assertTrue(query.contains("w.visibleUntil >= CURRENT_TIMESTAMP"));
        assertTrue(query.contains(":provinceCode"));
        assertTrue(query.contains(":districtCode"));
        assertTrue(query.contains(":warehouseTypeId"));
        assertTrue(query.contains(":minCapacity"));
        assertTrue(query.contains(":maxCapacity"));
        assertTrue(query.contains(":minPrice"));
        assertTrue(query.contains(":maxPrice"));
        assertFalse(query.contains("LOWER(w.provinceCode) LIKE"));
        assertFalse(query.contains("LOWER(w.districtCode) LIKE"));
    }

    @Test
    void warehouseDeclaresIndexesForLocationFilterColumns() {
        Table table = Warehouse.class.getAnnotation(Table.class);
        Set<String> indexNames = Arrays.stream(table.indexes())
                .map(jakarta.persistence.Index::name)
                .collect(Collectors.toSet());

        assertTrue(indexNames.contains("idx_warehouses_province_code"));
        assertTrue(indexNames.contains("idx_warehouses_district_code"));
        assertTrue(indexNames.contains("idx_warehouses_province_district"));
    }
}
