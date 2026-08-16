package fu.stockspace.stockspace_be.contract.service;
import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;








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




    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsTenant(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }



    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsOwner(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByOwnerId(ownerId, pageable)
                .map(this::mapToResponse);
    }



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




    @Transactional
    public void setDisputed(UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        contract.setStatus(ContractStatus.DISPUTED);
        contractRepository.save(contract);
    }

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
        if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) < 7) {
            throw new BadRequestException("Thời gian thuê kho tối thiểu là 7 ngày");
        }
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setPaperContractImages(request.getPaperContractImages().toString());
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        contract.setSubmittedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);
        log.info("Contract {} status updated to PENDING_TENANT_CONFIRM", contractId);


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

        contract = contractRepository.save(contract);
        log.info("Contract {} is now ACTIVE", contractId);
        return mapToResponse(contract);
    }



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

            contract.setStatus(ContractStatus.CANCELLED);
            warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());








            walletService.refundBalance(
                tenantId,
                contract.getBooking().getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn đặt cọc thuê kho do hai bên đồng thuận hủy: " + contract.getBooking().getWarehouse().getName(),
                contract.getBooking().getId(),
                null
            );


            contract.getBooking().setStatus(fu.stockspace.stockspace_be.booking.entity.ApprovalStatus.CANCELLED);
            contract.getBooking().setRejectReason("Hợp đồng bị hủy do hai bên đồng thuận hủy");
            bookingRepository.save(contract.getBooking());

            log.info("Contract {} cancelled by mutual agreement", contractId);
        } else {

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

            try {
                String warehouseName = contract.getBooking().getWarehouse().getName();
                UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();

                // 1. Thông báo cho Owner rằng Tenant không đồng ý hủy và đã mở tranh chấp
                notificationService.push(
                        ownerId,
                        "Khách thuê từ chối yêu cầu hủy",
                        "Khách thuê không đồng ý yêu cầu hủy hợp đồng kho '" + warehouseName + "'. Vụ việc đã được chuyển thành tranh chấp để Ban quản trị phân xử.",
                        "DISPUTE"
                );

                // 2. Thông báo cho Admin hệ thống
                userRepository.findFirstByRoles_Name(RoleType.ROLE_ADMIN.name())
                        .ifPresent(admin -> notificationService.push(
                                admin.getId(),
                                "Tranh chấp hợp đồng mới",
                                "Có một tranh chấp mới phát sinh từ việc từ chối hủy hợp đồng kho '" + warehouseName + "'. Vui lòng kiểm tra và phân xử.",
                                "DISPUTE"
                        ));
            } catch (Exception e) {
                log.warn("Failed to push dispute notification: {}", e.getMessage());
            }
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract);
    }
}
