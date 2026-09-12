package fu.stockspace.stockspace_be.wms.dataexchange.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.wms.dataexchange.config.DataExchangeProperties;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxFileException;
import fu.stockspace.stockspace_be.wms.dataexchange.xlsx.XlsxWorkbookWriter;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.time.LocalDateTime;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class WmsImportJobService {

    private final WmsImportJobRepository jobRepository;
    private final WmsImportRowRepository rowRepository;
    private final CanonicalContentHashService canonicalHashService;
    private final DataExchangeProperties properties;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    public WmsImportJob createJob(WmsImportJobCommand command) {
        validateCommand(command);
        List<WmsImportRowInput> inputs = command.rows() == null ? List.of() : command.rows();
        Map<String, Object> scope = scope(command);
        String contentSha256 = canonicalHashService.hash(command.importType(), scope, inputs);
        int invalidRows = (int) inputs.stream()
                .filter(row -> row.validationErrors() != null && !row.validationErrors().isEmpty())
                .count();

        WmsImportJob job = WmsImportJob.builder()
                .tenant(entityManager.getReference(User.class, command.tenantId()))
                .createdBy(entityManager.getReference(User.class, command.createdById()))
                .warehouse(command.warehouseId() == null ? null
                        : entityManager.getReference(Warehouse.class, command.warehouseId()))
                .audit(command.auditId() == null ? null
                        : entityManager.getReference(InventoryAudit.class, command.auditId()))
                .importType(command.importType())
                .status(invalidRows == 0 ? WmsImportJobStatus.VALIDATED : WmsImportJobStatus.INVALID)
                .schemaVersion(command.schemaVersion().trim())
                .originalFilename(normalizeFilename(command.originalFilename()))
                .fileSha256(command.fileSha256().toLowerCase(java.util.Locale.ROOT))
                .contentSha256(contentSha256)
                .contextMetadata(new LinkedHashMap<>(command.contextMetadata() == null
                        ? Map.of() : command.contextMetadata()))
                .totalRows(inputs.size())
                .validRows(inputs.size() - invalidRows)
                .invalidRows(invalidRows)
                .build();
        jobRepository.save(job);

        List<WmsImportRow> rows = new ArrayList<>();
        for (WmsImportRowInput input : inputs) {
            WmsImportRow row = new WmsImportRow();
            row.setJob(job);
            row.setSheetName(input.sheetName());
            row.setRowNumber(input.rowNumber());
            row.setGroupKey(input.groupKey());
            row.setNormalizedPayload(new LinkedHashMap<>(input.normalizedPayload() == null
                    ? Map.<String, Object>of() : input.normalizedPayload()));
            row.setValidationErrors(new ArrayList<>(input.validationErrors() == null
                    ? List.<Map<String, String>>of() : input.validationErrors()));
            rows.add(row);
        }
        rowRepository.saveAll(rows);
        return job;
    }

    public WmsImportJobResponse getJob(UUID tenantId, UUID actorId, UUID jobId) {
        WmsImportJob job = readableJob(tenantId, actorId, jobId);
        return toResponse(job, true);
    }

    public byte[] renderErrorWorkbook(UUID tenantId, UUID actorId, UUID jobId) {
        WmsImportJob job = readableJob(tenantId, actorId, jobId);
        if (job.getStatus() != WmsImportJobStatus.INVALID && job.getStatus() != WmsImportJobStatus.FAILED) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS,
                    "Chỉ có thể tải error workbook cho job INVALID hoặc FAILED");
        }
        List<WmsImportRow> rows = rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(jobId).stream()
                .filter(row -> row.getValidationErrors() != null && !row.getValidationErrors().isEmpty())
                .toList();
        try (Workbook workbook = XlsxWorkbookWriter.newWorkbook()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("schema_version", job.getSchemaVersion());
            metadata.put("workbook_type", "WMS_IMPORT_ERRORS");
            metadata.put("job_id", job.getId());
            metadata.put("tenant_id", job.getTenant().getId());
            XlsxWorkbookWriter.addMetadataSheet(workbook, metadata);
            Sheet sheet = workbook.createSheet("ERRORS");
            CellStyle header = XlsxWorkbookWriter.headerStyle(workbook);
            Row headerRow = sheet.createRow(0);
            XlsxWorkbookWriter.writeHeaders(headerRow, header,
                    "sheet_name", "row_number", "group_key", "normalized_payload",
                    "error_codes", "error_messages");
            int rowIndex = 1;
            for (WmsImportRow item : rows) {
                Row errorRow = sheet.createRow(rowIndex++);
                errorRow.createCell(0).setCellValue(XlsxWorkbookWriter.safeText(item.getSheetName()));
                errorRow.createCell(1).setCellValue(item.getRowNumber());
                errorRow.createCell(2).setCellValue(XlsxWorkbookWriter.safeText(item.getGroupKey()));
                errorRow.createCell(3).setCellValue(XlsxWorkbookWriter.safeText(
                        objectMapper.writeValueAsString(item.getNormalizedPayload())));
                errorRow.createCell(4).setCellValue(XlsxWorkbookWriter.safeText(errorCodes(item)));
                errorRow.createCell(5).setCellValue(XlsxWorkbookWriter.safeText(errorMessages(item)));
            }
            for (int column = 0; column < 6; column++) {
                sheet.autoSizeColumn(column);
            }
            return XlsxWorkbookWriter.toBytes(workbook);
        } catch (IOException ex) {
            throw new XlsxFileException(ErrorCode.WMS_IMPORT_FILE_INVALID,
                    "Unable to render import error workbook", ex);
        }
    }

    /**
     * Executes one domain apply under a pessimistic job lock. A domain failure
     * rolls back its transaction and is recorded as FAILED in a new transaction.
     */
    public <T> T applyJob(UUID tenantId, UUID actorId, UUID jobId,
                          Function<WmsImportJob, T> domainApply) {
        AtomicBoolean domainStarted = new AtomicBoolean(false);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            return transaction.execute(status -> {
                WmsImportJob job = jobRepository.findByIdForUpdate(jobId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND));
                assertReadable(job, tenantId, actorId);
                if (job.getStatus() != WmsImportJobStatus.VALIDATED) {
                    throw new ResourceConflictException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS);
                }
                if (jobRepository.existsByTenantIdAndImportTypeAndContentSha256AndStatusAndIsActiveTrueAndIsDeletedFalse(
                        tenantId, job.getImportType(), job.getContentSha256(), WmsImportJobStatus.APPLIED)) {
                    throw new ResourceConflictException(ErrorCode.WMS_IMPORT_ALREADY_APPLIED);
                }
                domainStarted.set(true);
                T result = domainApply.apply(job);
                job.setStatus(WmsImportJobStatus.APPLIED);
                job.setAppliedAt(LocalDateTime.now());
                job.setFailureMessage(null);
                jobRepository.saveAndFlush(job);
                return result;
            });
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("ux_wms_import_jobs_applied_content")) {
                throw new ResourceConflictException(ErrorCode.WMS_IMPORT_ALREADY_APPLIED);
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (domainStarted.get()) {
                recordFailure(jobId, ex.getMessage());
            }
            throw ex;
        }
    }

    public WmsImportJob getValidatedJobForTenant(UUID tenantId, UUID actorId, UUID jobId) {
        WmsImportJob job = readableJob(tenantId, actorId, jobId);
        if (job.getStatus() != WmsImportJobStatus.VALIDATED) {
            throw new ResourceConflictException(ErrorCode.WMS_IMPORT_JOB_INVALID_STATUS);
        }
        return job;
    }

    private WmsImportJob readableJob(UUID tenantId, UUID actorId, UUID jobId) {
        return jobRepository.findReadableByTenantAndActor(jobId, tenantId, actorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND));
    }

    private void assertReadable(WmsImportJob job, UUID tenantId, UUID actorId) {
        if (!tenantId.equals(job.getTenant().getId())
                || (!actorId.equals(job.getCreatedBy().getId()) && !actorId.equals(tenantId))) {
            throw new ResourceNotFoundException(ErrorCode.WMS_IMPORT_JOB_NOT_FOUND);
        }
    }

    private void recordFailure(UUID jobId, String message) {
        TransactionTemplate failureTransaction = new TransactionTemplate(transactionManager);
        failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        failureTransaction.executeWithoutResult(status -> jobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
            job.setStatus(WmsImportJobStatus.FAILED);
            job.setFailureMessage(message == null || message.isBlank()
                    ? "Import apply failed" : message.substring(0, Math.min(message.length(), 2000)));
            jobRepository.save(job);
        }));
    }

    private WmsImportJobResponse toResponse(WmsImportJob job, boolean includeErrors) {
        List<WmsImportRowErrorResponse> errors = List.of();
        if (includeErrors && job.getInvalidRows() > 0) {
            errors = rowRepository.findByJobIdOrderBySheetNameAscRowNumberAsc(job.getId()).stream()
                    .filter(row -> row.getValidationErrors() != null && !row.getValidationErrors().isEmpty())
                    .limit(properties.getMaxErrorsInline())
                    .map(row -> new WmsImportRowErrorResponse(row.getSheetName(), row.getRowNumber(),
                            row.getGroupKey(), row.getNormalizedPayload(), row.getValidationErrors()))
                    .toList();
        }
        return new WmsImportJobResponse(job.getId(), job.getImportType(), job.getStatus(), job.getSchemaVersion(),
                job.getOriginalFilename(), job.getFileSha256(), job.getContentSha256(), job.getContextMetadata(),
                job.getWarehouse() == null ? null : job.getWarehouse().getId(),
                job.getAudit() == null ? null : job.getAudit().getId(), job.getTotalRows(), job.getValidRows(),
                job.getInvalidRows(), job.getFailureMessage(), job.getCreatedAt(), job.getUpdatedAt(),
                job.getAppliedAt(), errors);
    }

    private Map<String, Object> scope(WmsImportJobCommand command) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("tenant_id", command.tenantId());
        scope.put("warehouse_id", command.warehouseId());
        scope.put("audit_id", command.auditId());
        return scope;
    }

    private void validateCommand(WmsImportJobCommand command) {
        if (command == null || command.tenantId() == null || command.createdById() == null
                || command.importType() == null || command.schemaVersion() == null
                || command.originalFilename() == null || command.fileSha256() == null) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID);
        }
        List<WmsImportRowInput> rows = command.rows() == null ? List.of() : command.rows();
        if (rows.size() > properties.getMaxRows()) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_LIMIT_EXCEEDED);
        }
        if (!command.fileSha256().matches("(?i)^[0-9a-f]{64}$")) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "File hash không hợp lệ");
        }
        if (command.schemaVersion().isBlank() || command.schemaVersion().length() > 20) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_SCHEMA_UNSUPPORTED);
        }
        if (command.importType() == WmsImportType.SKU_CATALOG
                && (command.warehouseId() != null || command.auditId() != null)) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "Catalog import không nhận warehouse/audit scope");
        }
        if (command.importType() == WmsImportType.OFFLINE_MOVEMENT
                && (command.warehouseId() == null || command.auditId() != null)) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "Movement import cần warehouse scope");
        }
        if (command.importType() == WmsImportType.AUDIT_RECONCILIATION
                && (command.warehouseId() == null || command.auditId() == null)) {
            throw new BadRequestException(ErrorCode.WMS_IMPORT_FILE_INVALID, "Audit import cần warehouse và audit scope");
        }
    }

    private String normalizeFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String errorCodes(WmsImportRow row) {
        return row.getValidationErrors().stream()
                .map(error -> error.getOrDefault("code", "IMPORT_ROW_INVALID"))
                .distinct()
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String errorMessages(WmsImportRow row) {
        return row.getValidationErrors().stream()
                .map(error -> error.getOrDefault("message", "Invalid row"))
                .reduce((left, right) -> left + "; " + right).orElse("");
    }
}
