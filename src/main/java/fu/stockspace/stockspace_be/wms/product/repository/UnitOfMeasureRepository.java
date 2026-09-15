package fu.stockspace.stockspace_be.wms.product.repository;

import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {

    @Query("""
            SELECT u FROM UnitOfMeasure u
            WHERE u.isDeleted = false
              AND u.isActive = true
              AND (
                    u.tenant.id = :tenantId
                    OR (
                        u.tenant IS NULL
                        AND NOT EXISTS (
                            SELECT 1 FROM UnitOfMeasure sub
                            WHERE sub.tenant.id = :tenantId
                              AND sub.code = u.code
                              AND sub.isDeleted = false
                              AND sub.isActive = true
                        )
                    )
              )
            """)
    Page<UnitOfMeasure> findAllActiveByTenantOrSystem(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
            SELECT u FROM UnitOfMeasure u
            WHERE u.isDeleted = false
              AND u.isActive = true
              AND UPPER(u.code) = UPPER(:code)
              AND (
                    u.tenant.id = :tenantId
                    OR (
                        u.tenant IS NULL
                        AND NOT EXISTS (
                            SELECT 1 FROM UnitOfMeasure sub
                            WHERE sub.tenant.id = :tenantId
                              AND UPPER(sub.code) = UPPER(u.code)
                              AND sub.isDeleted = false
                              AND sub.isActive = true
                        )
                    )
              )
            """)
    Optional<UnitOfMeasure> findActiveVisibleByCode(
            @Param("tenantId") UUID tenantId,
            @Param("code") String code);

    boolean existsByCode(String code);

    Optional<UnitOfMeasure> findByIdAndIsDeletedFalse(UUID id);
}
