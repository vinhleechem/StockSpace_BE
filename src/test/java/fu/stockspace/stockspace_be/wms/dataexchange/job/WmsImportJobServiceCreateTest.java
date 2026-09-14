package fu.stockspace.stockspace_be.wms.dataexchange.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WmsImportJobServiceCreateTest {

    @Mock
    private WmsImportJobRepository jobRepository;
    @Mock
    private WmsImportRowRepository rowRepository;
    @Mock
    private CanonicalContentHashService canonicalHashService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private WmsImportJobService service;
    private UUID tenantId;
    private UUID actorId;
    private User tenant;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new WmsImportJobService(
                jobRepository,
                rowRepository,
                canonicalHashService,
                new DataExchangeProperties(),
                objectMapper,
                entityManager,
                transactionManager);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        tenant = User.builder().id(tenantId).build();
        actor = User.builder().id(actorId).build();
        when(entityManager.getReference(User.class, tenantId)).thenReturn(tenant);
        when(entityManager.getReference(User.class, actorId)).thenReturn(actor);
        when(canonicalHashService.hash(any(), any(), any())).thenReturn("b".repeat(64));
    }

    @Test
    void createsRowsUsingTheManagedJobReturnedByRepository() {
        WmsImportJob persistedJob = persistedJob(WmsImportJobStatus.VALIDATED);
        when(jobRepository.saveAndFlush(any(WmsImportJob.class))).thenReturn(persistedJob);

        WmsImportJob result = service.createJob(command(
                WmsImportType.SKU_CATALOG,
                List.of(row("CATEGORIES", 2, List.of()), row("SKUS", 2, List.of()))));

        assertSame(persistedJob, result);
        ArgumentCaptor<List<WmsImportRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rowRepository).saveAll(rowsCaptor.capture());
        assertEquals(2, rowsCaptor.getValue().size());
        rowsCaptor.getValue().forEach(row -> assertSame(persistedJob, row.getJob()));

        InOrder order = inOrder(jobRepository, rowRepository);
        order.verify(jobRepository).saveAndFlush(any(WmsImportJob.class));
        order.verify(rowRepository).saveAll(any());
    }

    @Test
    void keepsInvalidStatusAndCountsWithoutChangingValidationSemantics() {
        WmsImportJob persistedJob = persistedJob(WmsImportJobStatus.INVALID);
        when(jobRepository.saveAndFlush(any(WmsImportJob.class))).thenReturn(persistedJob);

        WmsImportJob result = service.createJob(command(
                WmsImportType.SKU_CATALOG,
                List.of(row("SKUS", 2, List.of(Map.of("code", "SKU_INVALID"))))));

        assertSame(persistedJob, result);
        ArgumentCaptor<WmsImportJob> jobCaptor = ArgumentCaptor.forClass(WmsImportJob.class);
        verify(jobRepository).saveAndFlush(jobCaptor.capture());
        assertEquals(WmsImportJobStatus.INVALID, jobCaptor.getValue().getStatus());
        assertEquals(1, jobCaptor.getValue().getTotalRows());
        assertEquals(0, jobCaptor.getValue().getValidRows());
        assertEquals(1, jobCaptor.getValue().getInvalidRows());
    }

    @Test
    void doesNotSaveRowsWhenParentPersistenceFails() {
        when(jobRepository.saveAndFlush(any(WmsImportJob.class)))
                .thenThrow(new DataAccessException("parent persistence failed") { });

        assertThrows(DataAccessException.class, () -> service.createJob(command(
                WmsImportType.SKU_CATALOG,
                List.of(row("SKUS", 2, List.of())))));

        verify(rowRepository, never()).saveAll(any());
    }

    private WmsImportJobCommand command(WmsImportType type, List<WmsImportRowInput> rows) {
        return new WmsImportJobCommand(
                tenantId,
                actorId,
                null,
                null,
                type,
                "1.0",
                "catalog.xlsx",
                "a".repeat(64),
                Map.of(),
                rows);
    }

    private WmsImportRowInput row(String sheet, int rowNumber,
                                  List<Map<String, String>> errors) {
        return new WmsImportRowInput(
                sheet,
                rowNumber,
                null,
                Map.of("action", "SKIP"),
                errors);
    }

    private WmsImportJob persistedJob(WmsImportJobStatus status) {
        return WmsImportJob.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .createdBy(actor)
                .importType(WmsImportType.SKU_CATALOG)
                .status(status)
                .schemaVersion("1.0")
                .originalFilename("catalog.xlsx")
                .fileSha256("a".repeat(64))
                .contentSha256("b".repeat(64))
                .build();
    }
}
