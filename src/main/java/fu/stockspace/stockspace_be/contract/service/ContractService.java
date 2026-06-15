package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    // ==================== Internal ====================

    /**
     * Tạo RentalContract từ BookingRequest đã được APPROVED.
     * Mặc định: startDate = hôm nay, endDate = 1 tháng sau.
     * Gọi từ BookingService.approveBooking().
     */
    @Transactional
    public RentalContract createContractFromBooking(Long bookingId) {
        BookingRequest booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND));

        RentalContract contract = RentalContract.builder()
                .booking(booking)
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .tenantConfirmed(false)
                .ownerConfirmed(false)
                .build();

        contract = contractRepository.save(contract);
        log.info("RentalContract created: {} for booking {}", contract.getId(), bookingId);
        return contract;
    }

    // ==================== Query ====================

    /**
     * Xem danh sách hợp đồng của Tenant (phân trang).
     */
    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsTenant(Long tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Xem danh sách hợp đồng của Owner (phân trang).
     */
    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsOwner(Long ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByOwnerId(ownerId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Xem chi tiết hợp đồng — chỉ Owner hoặc Tenant liên quan mới xem được.
     */
    @Transactional(readOnly = true)
    public RentalContractResponse getContractById(Long contractId, Long userId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));

        Long tenantId = contract.getBooking().getTenant().getId();
        Long ownerId = contract.getBooking().getWarehouse().getOwner().getId();

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
    public RentalContractResponse confirmHandover(Long userId, Long contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
        }

        Long tenantId = contract.getBooking().getTenant().getId();
        Long ownerId = contract.getBooking().getWarehouse().getOwner().getId();

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
    public void setDisputed(Long contractId) {
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
                .build();
    }
}
