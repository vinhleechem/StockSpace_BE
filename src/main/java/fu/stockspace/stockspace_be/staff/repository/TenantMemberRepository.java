package fu.stockspace.stockspace_be.staff.repository;

import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {




    long countByTenantIdAndIsActiveTrueAndIsDeletedFalse(UUID tenantId);




    Page<TenantMember> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);





    Optional<TenantMember> findByUserIdAndTenantIdAndIsDeletedFalse(UUID userId, UUID tenantId);




    boolean existsByUserIdAndTenantIdAndIsDeletedFalse(UUID userId, UUID tenantId);




    boolean existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(UUID userId, UUID tenantId);





    Optional<TenantMember> findByUserIdAndIsActiveTrueAndIsDeletedFalse(UUID userId);




    List<TenantMember> findByUserIdOrderByJoinedAtDesc(UUID userId);






    @Query("SELECT m FROM TenantMember m WHERE m.tenant.id = :tenantId " +
           "AND m.isActive = true AND m.isDeleted = false " +
           "ORDER BY m.joinedAt ASC")
    List<TenantMember> findActiveStaffsOrderByJoinedAtAsc(@Param("tenantId") UUID tenantId);




    @Query("SELECT m FROM TenantMember m WHERE m.tenant.id = :tenantId AND m.isDeleted = false " +
           "AND (:keyword = '' OR LOWER(m.user.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR m.user.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<TenantMember> searchStaffs(@Param("tenantId") UUID tenantId,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);
}
