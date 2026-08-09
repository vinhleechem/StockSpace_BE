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

    /**
     * Đếm số nhân viên đang hoạt động của Tenant — dùng kiểm tra quota max_staff.
     */
    long countByTenantIdAndIsActiveTrueAndIsDeletedFalse(UUID tenantId);

    /**
     * Danh sách nhân viên của Tenant (chưa bị xóa mềm), hỗ trợ phân trang.
     */
    Page<TenantMember> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);

    /**
     * Tìm membership theo userId + tenantId (chưa bị xóa mềm).
     * Dùng khi Tenant muốn xóa / khóa nhân viên.
     */
    Optional<TenantMember> findByUserIdAndTenantIdAndIsDeletedFalse(UUID userId, UUID tenantId);

    /**
     * Kiểm tra User đã là nhân viên active của Tenant chưa.
     */
    boolean existsByUserIdAndTenantIdAndIsDeletedFalse(UUID userId, UUID tenantId);

    /**
     * Tìm membership active duy nhất của User (Staff chỉ làm 1 Tenant tại 1 thời điểm).
     * Dùng để embed tenantId vào JWT khi Staff đăng nhập.
     */
    Optional<TenantMember> findByUserIdAndIsActiveTrueAndIsDeletedFalse(UUID userId);

    /**
     * Lấy toàn bộ lịch sử các membership (Tenant công tác) của User qua các thời kỳ.
     */
    List<TenantMember> findByUserIdOrderByJoinedAtDesc(UUID userId);


    /**
     * Danh sách nhân viên active của Tenant, sắp xếp theo thời gian gia nhập tăng dần.
     * Dùng khi downgrade gói để xác định ai bị khóa (khóa người mới nhất).
     */
    @Query("SELECT m FROM TenantMember m WHERE m.tenant.id = :tenantId " +
           "AND m.isActive = true AND m.isDeleted = false " +
           "ORDER BY m.joinedAt ASC")
    List<TenantMember> findActiveStaffsOrderByJoinedAtAsc(@Param("tenantId") UUID tenantId);

    /**
     * Tìm kiếm nhân viên theo keyword (tên, email, SĐT) trong danh sách của Tenant.
     */
    @Query("SELECT m FROM TenantMember m WHERE m.tenant.id = :tenantId AND m.isDeleted = false " +
           "AND (:keyword = '' OR LOWER(m.user.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR m.user.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<TenantMember> searchStaffs(@Param("tenantId") UUID tenantId,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);
}
