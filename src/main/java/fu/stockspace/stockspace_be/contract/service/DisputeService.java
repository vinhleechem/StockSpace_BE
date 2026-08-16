package fu.stockspace.stockspace_be.contract.service;

import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.contract.dto.CreateDisputeRequest;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
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
    private final BookingRequestRepository bookingRepository;
    private final ContractService contractService;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final WalletService walletService;
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

        UUID tenantId = contract.getBooking().getTenant().getId();
        UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();
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
            String warehouseName = contract.getBooking().getWarehouse().getName();

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
        BookingRequest booking = contract.getBooking();
        String warehouseName = booking.getWarehouse().getName();
        UUID tenantId = booking.getTenant().getId();
        UUID ownerId = booking.getWarehouse().getOwner().getId();

        if ("REFUND_TO_TENANT".equalsIgnoreCase(depositResolution)) {
            walletService.refundBalance(
                tenantId,
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn đặt cọc phân xử tranh chấp: " + warehouseName,
                booking.getId(),
                null
            );

            log.info("Deposit resolved to be refunded to Tenant for contract {}", contract.getId());
        } else if ("FORFEIT_TO_OWNER".equalsIgnoreCase(depositResolution)) {
            walletService.refundBalance(
                ownerId,
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Nhận tiền cọc phạt cọc tranh chấp: " + warehouseName,
                booking.getId(),
                null
            );

            log.info("Deposit resolved to be forfeited to Owner for contract {}", contract.getId());
        }

        contract.setStatus(ContractStatus.CANCELLED);
        contractRepository.save(contract);
        warehouseService.markAsAvailable(booking.getWarehouse().getId());

        booking.setStatus(ApprovalStatus.CANCELLED);
        booking.setRejectReason("Hợp đồng bị hủy do tranh chấp được giải quyết bởi Admin");
        bookingRepository.save(booking);

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

    private DisputeResponse mapToResponse(DisputeTicket t) {
        return DisputeResponse.builder()
                .id(t.getId())
                .status(t.getStatus())
                .reason(t.getReason())
                .evidenceImages(t.getEvidenceImages())
                .adminNote(t.getAdminNote())
                .contractId(t.getContract() != null ? t.getContract().getId() : null)
                .raisedById(t.getRaisedBy() != null ? t.getRaisedBy().getId() : null)
                .raisedByName(t.getRaisedBy() != null ? t.getRaisedBy().getFullName() : null)
                .handledById(t.getHandledBy() != null ? t.getHandledBy().getId() : null)
                .handledByName(t.getHandledBy() != null ? t.getHandledBy().getFullName() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
