package fu.stockspace.stockspace_be.contract.service;

import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.CreateDisputeRequest;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {
    private final DisputeTicketRepository disputeRepository;
    private final RentalContractRepository contractRepository;
    private final ContractService contractService;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final NotificationService notificationService;

    @Transactional
    public DisputeResponse raiseDispute(UUID userId, CreateDisputeRequest request) {
        RentalContract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() == ContractStatus.COMPLETED
                || contract.getStatus() == ContractStatus.CANCELLED
                || contract.getStatus() == ContractStatus.DISPUTED) {
            throw new BadRequestException("Không thể mở tranh chấp cho hợp đồng ở trạng thái hiện tại");
        }

        boolean exists = disputeRepository.findByContractId(contract.getId()).isPresent();
        if (exists) {
            throw new ResourceConflictException(ErrorCode.DISPUTE_ALREADY_OPEN);
        }

        UUID tenantId = contract.getTenant().getId();
        UUID ownerId = contract.getOwner().getId();
        if (!userId.equals(tenantId) && !userId.equals(ownerId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String evidenceJson = request.getEvidenceImages() != null
                ? request.getEvidenceImages().toString()
                : null;
        DisputeTicket ticket = DisputeTicket.builder()
                .contract(contract)
                .raisedBy(user)
                .reason(request.getReason())
                .evidenceImages(evidenceJson)
                .status("OPEN")
                .build();
        ticket = disputeRepository.save(ticket);

        contractService.setDisputed(contract.getId());

        try {
            UUID notifyUserId = userId.equals(tenantId) ? ownerId : tenantId;
            String warehouseName = contract.getWarehouse().getName();

            // 1. Thông báo cho bên đối phương
            notificationService.push(
                    notifyUserId,
                    "Có tranh chấp mới cần xử lý",
                    "Một tranh chấp mới đã được mở cho hợp đồng kho " + warehouseName + ". Vui lòng kiểm tra.",
                    "DISPUTE"
            );

            // 2. Thông báo cho Admin hệ thống
            userRepository.findFirstByRoles_Name(RoleType.ROLE_ADMIN.name())
                    .ifPresent(admin -> notificationService.push(
                            admin.getId(),
                            "Tranh chấp hợp đồng mới",
                            "Một tranh chấp mới đã được mở cho hợp đồng kho '" + warehouseName + "'. Vui lòng kiểm tra và phân xử.",
                            "DISPUTE"
                    ));
        } catch (Exception e) {
            log.warn("Failed to push dispute notification: {}", e.getMessage());
        }

        log.info("Dispute {} opened by user {} for contract {}", ticket.getId(), userId, contract.getId());
        return mapToResponse(ticket);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> getMyDisputes(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return disputeRepository.findByInvolvedUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public DisputeResponse resolveDispute(UUID disputeId, UUID adminId, String adminNote, String depositResolution) {
        DisputeTicket ticket = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DISPUTE_NOT_FOUND));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        ticket.setStatus("RESOLVED");
        ticket.setHandledBy(admin);
        ticket.setAdminNote(adminNote);
        ticket = disputeRepository.save(ticket);

        RentalContract contract = ticket.getContract();
        String warehouseName = contract.getWarehouse().getName();
        UUID tenantId = contract.getTenant().getId();
        UUID ownerId = contract.getOwner().getId();

        contract.setStatus(ContractStatus.CANCELLED);
        contractRepository.save(contract);
        warehouseService.markAsAvailable(contract.getWarehouse().getId());

        try {
            String noteText = adminNote != null ? " Ghi chú: " + adminNote : "";
            String resTenantMsg = "REFUND_TO_TENANT".equalsIgnoreCase(depositResolution)
                    ? "Tranh chấp hợp đồng kho '" + warehouseName + "' đã được giải quyết. Tiền cọc đã được hoàn trả vào ví của bạn." + noteText
                    : "Tranh chấp hợp đồng kho '" + warehouseName + "' đã được giải quyết. Tiền cọc đã được khấu trừ chuyển cho Chủ kho." + noteText;

            String resOwnerMsg = "FORFEIT_TO_OWNER".equalsIgnoreCase(depositResolution)
                    ? "Tranh chấp hợp đồng kho '" + warehouseName + "' đã được giải quyết. Bạn đã được nhận tiền cọc bồi thường vào ví." + noteText
                    : "Tranh chấp hợp đồng kho '" + warehouseName + "' đã được giải quyết. Tiền cọc đã được hoàn trả cho Khách thuê." + noteText;

            notificationService.push(tenantId, "Kết quả giải quyết tranh chấp", resTenantMsg, "DISPUTE");
            notificationService.push(ownerId, "Kết quả giải quyết tranh chấp", resOwnerMsg, "DISPUTE");
        } catch (Exception e) {
            log.warn("Failed to push dispute resolution notifications: {}", e.getMessage());
        }

        log.info("Admin {} resolved dispute {} with depositResolution {}", adminId, disputeId, depositResolution);
        return mapToResponse(ticket);
    }

    public DisputeResponse mapToResponse(DisputeTicket t) {
        RentalContract contract = t.getContract();
        var warehouse = contract != null ? contract.getWarehouse() : null;
        var tenant = contract != null ? contract.getTenant() : null;
        var owner = contract != null ? contract.getOwner() : null;

        return DisputeResponse.builder()
                .id(t.getId())
                .status(t.getStatus())
                .reason(t.getReason())
                .evidenceImages(t.getEvidenceImages())
                .adminNote(t.getAdminNote())
                .contractId(contract != null ? contract.getId() : null)
                // Kho bãi
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .warehouseAddress(warehouse != null ? warehouse.getAddress() : null)
                // Thời hạn
                .startDate(contract != null ? contract.getStartDate() : null)
                .endDate(contract != null ? contract.getEndDate() : null)
                .paperContractFiles(contract != null ? contract.getPaperContractImages() : null)
                .paperContractImages(contract != null ? contract.getPaperContractImages() : null)
                // Thông tin Tenant
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantEmail(tenant != null ? tenant.getEmail() : null)
                .tenantPhone(tenant != null ? tenant.getPhone() : null)
                // Thông tin Owner
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .ownerPhone(owner != null ? owner.getPhone() : null)
                // Thông tin hủy ban đầu
                .cancelReason(contract != null ? contract.getCancelReason() : null)
                .cancelEvidence(contract != null ? contract.getCancelEvidence() : null)
                // Người tạo & Người xử lý
                .raisedById(t.getRaisedBy() != null ? t.getRaisedBy().getId() : null)
                .raisedByName(t.getRaisedBy() != null ? t.getRaisedBy().getFullName() : null)
                .handledById(t.getHandledBy() != null ? t.getHandledBy().getId() : null)
                .handledByName(t.getHandledBy() != null ? t.getHandledBy().getFullName() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
