package fu.stockspace.stockspace_be.inspection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.dto.SubmitInspectionRequest;
import fu.stockspace.stockspace_be.inspection.entity.InspectionReport;
import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import fu.stockspace.stockspace_be.inspection.repository.InspectionReportRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;









import java.util.UUID;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.notification.service.NotificationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionReportRepository inspectionRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final ObjectMapper objectMapper;
    private final WalletService walletService;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;







    @Transactional
    public InspectionReportResponse requestInspection(UUID ownerId, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED));

        if (warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            throw new BadRequestException(ErrorCode.INSPECTION_WAREHOUSE_NOT_AVAILABLE);
        }

        if (warehouse.isVerified()) {
            throw new ResourceConflictException(ErrorCode.WAREHOUSE_ALREADY_VERIFIED);
        }


        boolean hasPending = inspectionRepository.findByWarehouseId(warehouseId).stream()
                .anyMatch(r -> r.getStatus() == InspectionStatus.PENDING
                        || r.getStatus() == InspectionStatus.IN_PROGRESS);
        if (hasPending) {
            throw new BadRequestException(ErrorCode.INSPECTION_ALREADY_SUBMITTED);
        }


        BigDecimal fee = systemConfigService.getBigDecimalValue("inspection_fee", new BigDecimal("40000"));
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            walletService.deductBalance(
                    ownerId,
                    fee,
                    TransactionType.COMMISSION,
                    "Trừ phí yêu cầu kiểm định cho kho: " + warehouse.getName(),
                    null,
                    null
            );
        }

        InspectionReport report = InspectionReport.builder()
                .warehouse(warehouse)
                .status(InspectionStatus.PENDING)
                .build();

        report = inspectionRepository.save(report);
        log.info("Owner {} requested inspection for warehouse {} with fee {}", ownerId, warehouseId, fee);

        try {
            final String whName = warehouse.getName();
            userRepository.findFirstByRoles_Name(RoleType.ROLE_ADMIN.name())
                    .ifPresent(admin -> notificationService.push(
                            admin.getId(),
                            "Yêu cầu kiểm định kho mới",
                            "Chủ kho vừa gửi yêu cầu kiểm định cho kho '" + whName + "'. Vui lòng phân công thanh tra viên.",
                            "INSPECTION"
                    ));
        } catch (Exception e) {
            log.warn("Failed to push inspection request notification to admin: {}", e.getMessage());
        }

        return mapToResponse(report);
    }




    @Transactional(readOnly = true)
    public Page<InspectionReportResponse> getMyInspections(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return inspectionRepository.findByWarehouseOwnerId(ownerId, pageable)
                .map(this::mapToResponse);
    }






    @Transactional(readOnly = true)
    public Page<InspectionReportResponse> getAssignedInspections(UUID inspectorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return inspectionRepository.findByInspectorId(inspectorId, pageable)
                .map(this::mapToResponse);
    }





    @Transactional
    public InspectionReportResponse submitReport(UUID inspectorId, UUID inspectionId,
                                                  SubmitInspectionRequest request) {
        InspectionReport report = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INSPECTION_NOT_FOUND));


        if (report.getInspector() == null || !report.getInspector().getId().equals(inspectorId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }


        if (report.getStatus() != InspectionStatus.IN_PROGRESS) {
            throw new BadRequestException(ErrorCode.INSPECTION_ALREADY_SUBMITTED);
        }


        if (request.getStatus() != InspectionStatus.PASSED
                && request.getStatus() != InspectionStatus.FAILED) {
            throw new BadRequestException(ErrorCode.INSPECTION_ALREADY_SUBMITTED);
        }


        String checklistJson = null;
        if (request.getChecklistData() != null) {
            try {
                checklistJson = objectMapper.writeValueAsString(request.getChecklistData());
            } catch (JsonProcessingException e) {
                checklistJson = request.getChecklistData().toString();
            }
        }

        report.setStatus(request.getStatus());
        report.setNotes(request.getNotes());
        report.setChecklistData(checklistJson);
        if (request.getImages() != null) {
            report.getImages().clear();
            report.getImages().addAll(request.getImages());
        }
        report.setInspectedAt(LocalDateTime.now());
        report = inspectionRepository.save(report);


        if (request.getStatus() == InspectionStatus.PASSED) {
            warehouseService.markAsVerifiedByInspection(report.getWarehouse().getId());
            log.info("Inspector {} submitted PASSED report for warehouse {}",
                    inspectorId, report.getWarehouse().getId());
        } else {
            log.info("Inspector {} submitted FAILED report for warehouse {}",
                    inspectorId, report.getWarehouse().getId());
        }

        if (report.getWarehouse() != null && report.getWarehouse().getOwner() != null) {
            String warehouseName = report.getWarehouse().getName();
            UUID ownerId = report.getWarehouse().getOwner().getId();
            if (request.getStatus() == InspectionStatus.PASSED) {
                notificationService.push(
                        ownerId,
                        "Kết quả kiểm định kho bãi",
                        "Kho bãi '" + warehouseName + "' của bạn đã đạt kiểm định (PASSED) và được gắn nhãn xác minh thành công.",
                        "SYSTEM"
                );
            } else {
                notificationService.push(
                        ownerId,
                        "Kết quả kiểm định kho bãi",
                        "Yêu cầu kiểm định kho bãi '" + warehouseName + "' của bạn đã bị từ chối (FAILED). Lý do: " + (request.getNotes() != null ? request.getNotes() : "Không có ghi chú thêm"),
                        "SYSTEM"
                );
            }
        }

        return mapToResponse(report);
    }







    @Transactional
    public InspectionReportResponse assignInspector(UUID inspectionId, UUID inspectorId) {
        InspectionReport report = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INSPECTION_NOT_FOUND));

        if (report.getStatus() != InspectionStatus.PENDING) {
            throw new BadRequestException(ErrorCode.INSPECTION_ALREADY_SUBMITTED);
        }

        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        report.setInspector(inspector);
        report.setStatus(InspectionStatus.IN_PROGRESS);
        report = inspectionRepository.save(report);

        if (report.getInspector() != null && report.getWarehouse() != null) {
            notificationService.push(
                    report.getInspector().getId(),
                    "Yêu cầu kiểm định mới",
                    "Bạn đã được phân công kiểm định kho bãi '" + report.getWarehouse().getName() + "'. Vui lòng kiểm tra thông tin chi tiết.",
                    "SYSTEM"
            );
        }

        log.info("Admin assigned inspector {} to inspection {}", inspectorId, inspectionId);
        return mapToResponse(report);
    }




    @Transactional(readOnly = true)
    public Page<InspectionReportResponse> getAllInspections(InspectionStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return inspectionRepository.findAllWithFilter(status, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public InspectionReportResponse getInspectionById(UUID inspectionId) {
        InspectionReport report = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INSPECTION_NOT_FOUND));
        return mapToResponse(report);
    }




    private InspectionReportResponse mapToResponse(InspectionReport r) {
        var warehouse = r.getWarehouse();
        var inspector = r.getInspector();
        var owner = warehouse != null ? warehouse.getOwner() : null;

        return InspectionReportResponse.builder()
                .id(r.getId())
                .status(r.getStatus().name())
                .checklistData(r.getChecklistData())
                .notes(r.getNotes())
                .images(r.getImages())
                .inspectedAt(r.getInspectedAt())
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .warehouseAddress(warehouse != null ? warehouse.getAddress() : null)
                .inspectorId(inspector != null ? inspector.getId() : null)
                .inspectorName(inspector != null ? inspector.getFullName() : null)
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
