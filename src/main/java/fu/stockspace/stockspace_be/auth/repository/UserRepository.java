package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.User;
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
public interface UserRepository extends JpaRepository<User, UUID> {




    @Query("SELECT u FROM User u WHERE u.id = ?1 AND u.isDeleted = false")
    Optional<User> findById(UUID id);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false")
    List<User> findAll();

    @Query("SELECT u FROM User u WHERE u.isDeleted = false")
    Page<User> findAll(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false")
    long count();




    @Query("SELECT u FROM User u WHERE u.email = ?1 AND u.isDeleted = false")
    Optional<User> findByEmail(String email);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r " +
           "WHERE LOWER(u.email) = LOWER(:email) " +
           "AND u.isActive = true AND u.isDeleted = false " +
           "AND r.name = :roleName")
    Optional<User> findActiveByEmailAndRole(@Param("email") String email,
                                            @Param("roleName") String roleName);




    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = ?1 AND u.isDeleted = false")
    boolean existsByEmail(String email);




    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId AND u.isDeleted = false")
    List<User> findUsersByRoleId(@Param("roleId") java.util.UUID roleId);





    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
           "(:keyword = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);




    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.isDeleted = false AND " +
           "r.name = :roleName AND " +
           "(:keyword = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsersByRole(@Param("keyword") String keyword,
                                 @Param("roleName") String roleName,
                                 Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
           "u.isActive = :isActive AND " +
           "(:keyword = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsersByStatus(@Param("keyword") String keyword,
                                   @Param("isActive") boolean isActive,
                                   Pageable pageable);

    Optional<User> findFirstByRoles_Name(String roleName);
}

