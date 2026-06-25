package fu.stockspace.stockspace_be.wms.product.repository;

import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    @Query("SELECT c FROM ProductCategory c WHERE c.isDeleted = false AND (c.tenant.id = :tenantId OR c.tenant IS NULL)")
    List<ProductCategory> findAllActiveByTenantOrSystem(@Param("tenantId") UUID tenantId);

    Optional<ProductCategory> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT c FROM ProductCategory c WHERE c.id = :id AND c.isDeleted = false AND (c.tenant.id = :tenantId OR c.tenant IS NULL)")
    Optional<ProductCategory> findByIdAndTenantIdOrSystemAndIsDeletedFalse(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
