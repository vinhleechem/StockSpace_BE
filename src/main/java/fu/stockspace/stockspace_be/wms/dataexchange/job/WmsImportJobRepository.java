package fu.stockspace.stockspace_be.wms.dataexchange.job;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WmsImportJobRepository extends JpaRepository<WmsImportJob, UUID> {

    Optional<WmsImportJob> findByIdAndTenantIdAndIsDeletedFalse(UUID id, UUID tenantId);

    Optional<WmsImportJob> findByIdAndCreatedByIdAndIsDeletedFalse(UUID id, UUID createdById);

    @Query("""
            select j from WmsImportJob j
            where j.id = :jobId
              and j.tenant.id = :tenantId
              and j.isActive = true
              and j.isDeleted = false
              and (j.createdBy.id = :actorId or j.createdBy.id = :tenantId)
            """)
    Optional<WmsImportJob> findReadableByTenantAndActor(
            @Param("jobId") UUID jobId,
            @Param("tenantId") UUID tenantId,
            @Param("actorId") UUID actorId);

    boolean existsByTenantIdAndImportTypeAndContentSha256AndStatusAndIsActiveTrueAndIsDeletedFalse(
            UUID tenantId,
            WmsImportType importType,
            String contentSha256,
            WmsImportJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from WmsImportJob j where j.id = :id and j.isDeleted = false")
    Optional<WmsImportJob> findByIdForUpdate(@Param("id") UUID id);
}
