package fu.stockspace.stockspace_be.contract.service;
import java.util.UUID;
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
/**
 * Service xử lý nghiệp vụ Dispute Ticket.
 *
 * Chức năng:
 * - Mở tranh chấp → đổi Contract sang DISPUTED
 * - Xem dispute của mình
 * - Admin giải quyết dispute → gọi từ AdminDisputeService
 */
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
    // ==================== User ====================
    /**
     * Mở tranh chấp cho hợp đồng.
     *
     * Ràng buộc:
     * - Hợp đồng phải ACTIVE hoặc PENDING_HANDOVER
     * - Chỉ Owner hoặc Tenant của hợp đồng được mở
     * - Mỗi hợp đồng chỉ có 1 dispute tại một thời điểm
     */
    @Transactional
    public DisputeResponse raiseDispute(UUID userId, CreateDisputeRequest request) {
        RentalContract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        // Chỉ cho phép dispute khi contract chưa hoàn thành/hủy và chưa có dispute
        if (contract.getStatus() == ContractStatus.COMPLETED
                || contract.getStatus() == ContractStatus.CANCELLED
                || contract.getStatus() == ContractStatus.DISPUTED) {
            throw new BadRequestException("Không thể mở tranh chấp cho hợp đồng ở trạng thái hiện tại");
        }
        // Kiểm tra đã có dispute chưa
        boolean exists = disputeRepository.findByContractId(contract.getId()).isPresent();
        if (exists) {
            throw new ResourceConflictException(ErrorCode.DISPUTE_ALREADY_OPEN);
        }
        // Kiểm tra user là tenant hoặc owner của hợp đồng
        UUID tenantId = contract.getBooking().getTenant().getId();
        UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!userId.equals(tenantId) && !userId.equals(ownerId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        // Chuyển ảnh thành JSON string đơn giản
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
        // Đổi contract → DISPUTED
        contractService.setDisputed(contract.getId());

        // Push thông báo cho bên kia trong hợp đồng (owner hoặc tenant)
        try {
            UUID notifyUserId = userId.equals(tenantId) ? ownerId : tenantId;
            String warehouseName = contract.getBooking().getWarehouse().getName();
            notificationService.push(
                    notifyUserId,
                    "Có tranh chấp mới cần xử lý",
                    "Một tranh chấp mới đã được mở cho hợp đồng kho " + warehouseName + ". Vui lòng kiểm tra.",
                    "DISPUTE"
            );
        } catch (Exception e) {
            log.warn("Failed to push dispute notification: {}", e.getMessage());
        }

        log.info("Dispute {} opened by user {} for contract {}", ticket.getId(), userId, contract.getId());
        return mapToResponse(ticket);
    }
    /**
     * Xem danh sách dispute của user hiện tại.
     */
    @Transactional(readOnly = true)
    public Page<DisputeResponse> getMyDisputes(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return disputeRepository.findByRaisedById(userId, pageable)
                .map(this::mapToResponse);
    }
    // ==================== Admin internal ====================
    /**
     * Admin giải quyết dispute.
     * Gọi từ AdminDisputeService.
     */
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
        // Xử lý cọc theo kết quả phân xử của Inspector/Admin
        RentalContract contract = ticket.getContract();
        BookingRequest booking = contract.getBooking();
        
        if ("REFUND_TO_TENANT".equalsIgnoreCase(depositResolution)) {
            // =========================================================
            // [INTEGRATION POINT — Dev B]
            // Hoàn cọc cho Tenant:
            // walletService.refundBalance(booking.getTenant().getId(), booking.getDepositAmount(), "Hoàn đặt cọc phân xử tranh chấp...");
            walletService.refundBalance(
                booking.getTenant().getId(),
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn đặt cọc phân xử tranh chấp: " + booking.getWarehouse().getName(),
                booking.getId(),
                null
            );
            // =========================================================
            log.info("Deposit resolved to be refunded to Tenant for contract {}", contract.getId());
        } else if ("FORFEIT_TO_OWNER".equalsIgnoreCase(depositResolution)) {
            // =========================================================
            // [INTEGRATION POINT — Dev B]
            // Phạt cọc, chuyển cho Owner:
            // walletService.addBalance(booking.getWarehouse().getOwner().getId(), booking.getDepositAmount(), "Nhận tiền cọc phạt cọc tranh chấp...");
            walletService.refundBalance(
                booking.getWarehouse().getOwner().getId(),
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Nhận tiền cọc phạt cọc tranh chấp: " + booking.getWarehouse().getName(),
                booking.getId(),
                null
            );
            // =========================================================
            log.info("Deposit resolved to be forfeited to Owner for contract {}", contract.getId());
        }
        // Hủy bỏ hợp đồng/deal và khôi phục trạng thái kho bãi về AVAILABLE
        contract.setStatus(ContractStatus.CANCELLED);
        contractRepository.save(contract);
        warehouseService.markAsAvailable(booking.getWarehouse().getId());

        // [FIX] Đặt BookingRequest cũ → CANCELLED để không block việc booking lại kho này
        booking.setStatus(ApprovalStatus.CANCELLED);
        booking.setRejectReason("Hợp đồng bị hủy do tranh chấp được giải quyết bởi Admin");
        bookingRepository.save(booking);

        log.info("Admin {} resolved dispute {} with depositResolution {}", adminId, disputeId, depositResolution);
        return mapToResponse(ticket);
    }
    // ==================== Private helpers ====================
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
