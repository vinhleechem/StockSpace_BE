package fu.stockspace.stockspace_be.wms.product.repository;

import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, UUID> {

    @Query("SELECT s FROM ProductSku s WHERE s.isDeleted = false AND (s.tenant.id = :tenantId OR (s.tenant IS NULL AND NOT EXISTS (SELECT 1 FROM ProductSku sub WHERE sub.tenant.id = :tenantId AND sub.skuCode = s.skuCode AND sub.isDeleted = false)))")
    Page<ProductSku> findAllActiveByTenantOrSystem(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT COUNT(s) > 0 FROM ProductSku s WHERE s.isDeleted = false AND s.skuCode = :skuCode AND ((:tenantId IS NULL AND s.tenant IS NULL) OR (s.tenant.id = :tenantId))")
    boolean existsBySkuCodeAndTenantOrSystem(@Param("skuCode") String skuCode, @Param("tenantId") UUID tenantId);

    Optional<ProductSku> findByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT s FROM ProductSku s WHERE s.id = :id AND s.isDeleted = false AND (s.tenant.id = :tenantId OR s.tenant IS NULL)")
    Optional<ProductSku> findByIdAndTenantIdOrSystemAndIsDeletedFalse(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    boolean existsByCategoryIdAndIsDeletedFalse(UUID categoryId);
}
