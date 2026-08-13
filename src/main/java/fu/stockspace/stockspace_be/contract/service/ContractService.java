package fu.stockspace.stockspace_be.contract.service;
import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;

import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.*;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Service xử lý nghiệp vụ RentalContract.
 *
 * Chức năng:
 * - Tạo hợp đồng từ BookingRequest (internal, gọi từ BookingService)
 * - Xem hợp đồng (Owner / Tenant)
 * - Xác nhận bàn giao — khi cả 2 confirm → COMPLETED + warehouse AVAILABLE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {
    private final RentalContractRepository contractRepository;
    private final BookingRequestRepository bookingRepository;
    private final WarehouseService warehouseService;
    private final DisputeTicketRepository disputeRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final WarehouseLayoutService warehouseLayoutService;
    private final NotificationService notificationService;
    // ==================== Internal ====================
    /**
     * Tạo RentalContract từ BookingRequest đã được APPROVED.
     * Mặc định: startDate = hôm nay, endDate = 1 tháng sau.
     * Gọi từ BookingService.approveBooking().
     */
    @Transactional
    public RentalContract createContractFromBooking(UUID bookingId) {
        BookingRequest booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND));
        RentalContract contract = RentalContract.builder()
                .booking(booking)
                .status(ContractStatus.UNDER_NEGOTIATION)
                .startDate(null)
                .endDate(null)
                .tenantConfirmed(false)
                .ownerConfirmed(false)
                .build();
        contract = contractRepository.save(contract);
        log.info("RentalContract created: {} in UNDER_NEGOTIATION state for booking {}", contract.getId(), bookingId);
        return contract;
    }
    // ==================== Query ====================
    /**
     * Xem danh sách hợp đồng của Tenant (phân trang).
     */
    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsTenant(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }
    /**
     * Xem danh sách hợp đồng của Owner (phân trang).
     */
    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsOwner(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByOwnerId(ownerId, pageable)
                .map(this::mapToResponse);
    }
    /**
     * Xem chi tiết hợp đồng — chỉ Owner hoặc Tenant liên quan mới xem được.
     */
    @Transactional(readOnly = true)
    public RentalContractResponse getContractById(UUID contractId, UUID userId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID tenantId = contract.getBooking().getTenant().getId();
        UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!userId.equals(tenantId) && !userId.equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return mapToResponse(contract);
    }


    // ==================== Confirm handover ====================
    /**
     * Một bên xác nhận bàn giao kho.
     *
     * Khi cả 2 bên confirm:
     * - Contract status → COMPLETED
     * - Warehouse status → AVAILABLE
     */
    @Transactional
    public RentalContractResponse confirmHandover(UUID userId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
        }
        UUID tenantId = contract.getBooking().getTenant().getId();
        UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (userId.equals(tenantId)) {
            if (contract.isTenantConfirmed()) {
                throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
            }
            contract.setTenantConfirmed(true);
            log.info("Tenant {} confirmed handover for contract {}", userId, contractId);
        } else if (userId.equals(ownerId)) {
            if (contract.isOwnerConfirmed()) {
                throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
            }
            contract.setOwnerConfirmed(true);
            log.info("Owner {} confirmed handover for contract {}", userId, contractId);
        } else {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        // Cả 2 bên đã confirm → hoàn thành hợp đồng
        if (contract.isTenantConfirmed() && contract.isOwnerConfirmed()) {
            contract.setStatus(ContractStatus.COMPLETED);
            warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());
            log.info("Contract {} COMPLETED — warehouse {} is now AVAILABLE",
                    contractId, contract.getBooking().getWarehouse().getId());
        } else {
            contract.setStatus(ContractStatus.PENDING_HANDOVER);
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract);
    }
    // ==================== Admin internal ====================
    /**
     * Admin / Dispute handler: set contract status = DISPUTED.
     */
    @Transactional
    public void setDisputed(UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        contract.setStatus(ContractStatus.DISPUTED);
        contractRepository.save(contract);
    }
    // ==================== Private helpers ====================
    public RentalContractResponse mapToResponse(RentalContract c) {
        BookingRequest b = c.getBooking();
        var tenant = b.getTenant();
        var warehouse = b.getWarehouse();
        var owner = warehouse != null ? warehouse.getOwner() : null;
        return RentalContractResponse.builder()
                .id(c.getId())
                .status(c.getStatus().name())
                .tenantConfirmed(c.isTenantConfirmed())
                .ownerConfirmed(c.isOwnerConfirmed())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .paperContractImages(c.getPaperContractImages())
                .bookingId(b.getId())
                .depositAmount(b.getDepositAmount())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantEmail(tenant != null ? tenant.getEmail() : null)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .warehouseAddress(warehouse != null ? warehouse.getAddress() : null)
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .submittedAt(c.getSubmittedAt())
                .cancelReason(c.getCancelReason())
                .cancelEvidence(c.getCancelEvidence())
                .build();
    }
    // ==================== Phase 1 Deal / Negotiation ====================
    /**
     * Owner cấu hình hợp đồng online sau khi ký hợp đồng giấy thành công.
     */
    @Transactional
    public RentalContractResponse submitOnlineContract(UUID ownerId, UUID contractId, SubmitContractRequest request) {
        log.info("Owner {} submitting online contract for contract {}", ownerId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualOwnerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!ownerId.equals(actualOwnerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION) {
            throw new BadRequestException("Hợp đồng không ở trạng thái thương lượng");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        }
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setPaperContractImages(request.getPaperContractImages().toString());
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        contract.setSubmittedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);
        log.info("Contract {} status updated to PENDING_TENANT_CONFIRM", contractId);

        // Push thông báo cho Tenant (Module 8 — Dev B)
        try {
            UUID tenantId = contract.getBooking().getTenant().getId();
            String warehouseName = contract.getBooking().getWarehouse().getName();
            notificationService.push(
                    tenantId,
                    "Hợp đồng cần xác nhận",
                    "Owner đã cập nhật hợp đồng kho " + warehouseName + ". Bạn có 7 ngày để ký xác nhận.",
                    "CONTRACT"
            );
        } catch (Exception e) {
            log.warn("Failed to push contract notification to tenant: {}", e.getMessage());
        }

        return mapToResponse(contract);
    }
    /**
     * Tenant xác nhận kích hoạt hợp đồng (trong hạn 7 ngày).
     */
    @Transactional
    public RentalContractResponse tenantConfirmContract(UUID tenantId, UUID contractId) {
        log.info("Tenant {} confirming contract {}", tenantId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Hợp đồng không ở trạng thái chờ xác nhận");
        }
        // Kiểm tra thời hạn 7 ngày
        if (contract.getSubmittedAt() != null && contract.getSubmittedAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Hợp đồng đã quá hạn 7 ngày để xác nhận. Tiền cọc đã bị xử lý.");
        }
        contract.setTenantConfirmed(true);
        contract.setOwnerConfirmed(true);
        contract.setStatus(ContractStatus.ACTIVE);

        try {
            warehouseLayoutService.cloneLayout(contract.getBooking().getWarehouse().getId(), actualTenantId);
        } catch (Exception e) {
            log.error("Failed to auto-clone layout for tenant {} after contract activation: {}", actualTenantId, e.getMessage());
        }

        // Chuyển tiền cọc sang ví Owner khi Tenant confirm hợp đồng
        BigDecimal depositAmount = contract.getBooking().getDepositAmount();
        if (depositAmount == null) {
            depositAmount = BigDecimal.ZERO;
        }
        walletService.refundBalance(
            contract.getBooking().getWarehouse().getOwner().getId(),
            depositAmount,
            TransactionType.DEPOSIT_RECEIVED,
            "Nhận cọc thuê kho: " + contract.getBooking().getWarehouse().getName(),
            contract.getBooking().getId(),
            null
        );
        // =========================================================
        contract = contractRepository.save(contract);
        log.info("Contract {} is now ACTIVE", contractId);
        return mapToResponse(contract);
    }
    /**
     * Tenant báo thương thảo thất bại (report trước hoặc sau khi owner submit hợp đồng).
     */
    @Transactional
    public RentalContractResponse tenantReportFailed(UUID tenantId, UUID contractId, TenantReportFailedRequest request) {
        log.info("Tenant {} reporting contract failed: {}", tenantId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION 
                && contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Không thể báo cáo sự cố hợp đồng ở trạng thái hiện tại");
        }
        // Kiểm tra đã có tranh chấp chưa
        if (disputeRepository.findByContractId(contract.getId()).isPresent()) {
            throw new BadRequestException("Hợp đồng này đã có tranh chấp đang mở");
        }
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        String evidenceJson = request.getEvidenceImages() != null ? request.getEvidenceImages().toString() : null;
        DisputeTicket ticket = DisputeTicket.builder()
                .contract(contract)
                .raisedBy(tenant)
                .reason(request.getReason())
                .evidenceImages(evidenceJson)
                .status("OPEN")
                .build();
        disputeRepository.save(ticket);
        contract.setStatus(ContractStatus.DISPUTED);
        contract = contractRepository.save(contract);
        log.info("Dispute ticket opened for contract {} by Tenant", contractId);
        return mapToResponse(contract);
    }
    /**
     * Owner đề xuất hủy thương lượng (Cancel deal).
     */
    @Transactional
    public RentalContractResponse ownerRequestCancel(UUID ownerId, UUID contractId, OwnerCancelRequest request) {
        log.info("Owner {} requesting cancellation for contract {}", ownerId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualOwnerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!ownerId.equals(actualOwnerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION 
                && contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Không thể đề xuất hủy hợp đồng ở trạng thái hiện tại");
        }
        contract.setCancelReason(request.getReason());
        String evidenceJson = request.getEvidenceImages() != null ? request.getEvidenceImages().toString() : null;
        contract.setCancelEvidence(evidenceJson);
        contract.setStatus(ContractStatus.PENDING_CANCEL);
        contract = contractRepository.save(contract);
        log.info("Contract {} is now PENDING_CANCEL", contractId);
        return mapToResponse(contract);
    }
    /**
     * Tenant phản hồi yêu cầu hủy deal của Owner.
     */
    @Transactional
    public RentalContractResponse tenantRespondCancel(UUID tenantId, UUID contractId, boolean agree) {
        log.info("Tenant {} responding to cancel request for contract {}, agree={}", tenantId, contractId, agree);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.PENDING_CANCEL) {
            throw new BadRequestException("Hợp đồng không có yêu cầu hủy nào đang chờ phản hồi");
        }
        if (agree) {
            // Tenant đồng ý hủy -> hoàn cọc 10%, trả lại kho về AVAILABLE
            contract.setStatus(ContractStatus.CANCELLED);
            warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());
            // =========================================================
            // [INTEGRATION POINT — Dev B]
            // Hoàn cọc 10% cho Tenant:
            // walletService.refundBalance(
            //     tenantId,
            //     contract.getBooking().getDepositAmount(),
            //     "Hoàn đặt cọc thuê kho do hai bên đồng thuận hủy: " + contract.getBooking().getWarehouse().getName()
            // );
            walletService.refundBalance(
                tenantId,
                contract.getBooking().getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn đặt cọc thuê kho do hai bên đồng thuận hủy: " + contract.getBooking().getWarehouse().getName(),
                contract.getBooking().getId(),
                null
            );
            // =========================================================
            // [FIX] Reset BookingRequest cũ → CANCELLED để không block booking lại kho sau này
            contract.getBooking().setStatus(fu.stockspace.stockspace_be.booking.entity.ApprovalStatus.CANCELLED);
            contract.getBooking().setRejectReason("Hợp đồng bị hủy do hai bên đồng thuận hủy");
            bookingRepository.save(contract.getBooking());
            // =========================================================
            log.info("Contract {} cancelled by mutual agreement", contractId);
        } else {
            // Tenant KHÔNG đồng ý hủy -> chuyển thành DISPUTED để Inspector giải quyết
            if (disputeRepository.findByContractId(contract.getId()).isPresent()) {
                throw new BadRequestException("Hợp đồng này đã có tranh chấp đang mở");
            }
            User tenant = userRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            DisputeTicket ticket = DisputeTicket.builder()
                    .contract(contract)
                    .raisedBy(tenant)
                    .reason("Tenant không đồng ý yêu cầu hủy thương lượng của Owner. Lý do hủy của Owner: " + contract.getCancelReason())
                    .evidenceImages(contract.getCancelEvidence())
                    .status("OPEN")
                    .build();
            disputeRepository.save(ticket);
            contract.setStatus(ContractStatus.DISPUTED);
            log.info("Tenant disagreed to cancel. Contract {} status changed to DISPUTED", contractId);
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract);
    }
}
