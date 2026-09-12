package fu.stockspace.stockspace_be.wms.dataexchange.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WmsImportJobServiceApplyTest {

    @Mock
    private WmsImportJobRepository jobRepository;
    @Mock
    private WmsImportRowRepository rowRepository;
    @Mock
    private CanonicalContentHashService canonicalHashService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @InjectMocks
    private WmsImportJobService service;

    private UUID tenantId;
    private UUID jobId;
    private WmsImportJob job;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        job = WmsImportJob.builder()
                .id(jobId)
                .tenant(tenant)
                .createdBy(tenant)
                .importType(WmsImportType.SKU_CATALOG)
                .status(WmsImportJobStatus.VALIDATED)
                .schemaVersion("1.0")
                .originalFilename("catalog.xlsx")
                .fileSha256("a".repeat(64))
                .contentSha256("b".repeat(64))
                .build();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void appliesValidatedJobOnceAndMarksItApplied() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.existsByTenantIdAndImportTypeAndContentSha256AndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, WmsImportType.SKU_CATALOG, job.getContentSha256(), WmsImportJobStatus.APPLIED))
                .thenReturn(false);

        String result = service.applyJob(tenantId, tenantId, jobId, ignored -> "applied");

        assertEquals("applied", result);
        assertEquals(WmsImportJobStatus.APPLIED, job.getStatus());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void rejectsSecondApplyBeforeDomainCallback() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.existsByTenantIdAndImportTypeAndContentSha256AndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, WmsImportType.SKU_CATALOG, job.getContentSha256(), WmsImportJobStatus.APPLIED))
                .thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> service.applyJob(tenantId, tenantId, jobId, ignored -> {
                    throw new AssertionError("domain callback must not run");
                }));

        assertEquals(WmsImportJobStatus.VALIDATED, job.getStatus());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordsDomainFailureWithoutMarkingJobApplied() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.existsByTenantIdAndImportTypeAndContentSha256AndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, WmsImportType.SKU_CATALOG, job.getContentSha256(), WmsImportJobStatus.APPLIED))
                .thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.applyJob(tenantId, tenantId, jobId,
                        ignored -> { throw new IllegalStateException("domain failed"); }));

        assertEquals(WmsImportJobStatus.FAILED, job.getStatus());
        verify(jobRepository).save(job);
    }
}
