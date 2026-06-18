package fu.stockspace.stockspace_be.booking.service;
import java.util.UUID;
import java.math.BigDecimal;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.dto.*;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import fu.stockspace.stockspace_be.common.repository.SystemPolicyRepository;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Service xử lý nghiệp vụ Booking Request (Thuê kho).
 *
 * Luồng chính:
 *   1. Tenant gửi yêu cầu thuê → status PENDING
 *   2. Owner approve → deductBalance (Dev B) + tạo RentalContract + Warehouse RENTED
 *   3. Owner reject  → status REJECTED (không deduct)
 *   4. Tenant cancel → status REJECTED (nếu còn PENDING)
 *
 * ⚠️ Dependency với Dev B:
 *   - approveBooking() cần gọi WalletService.deductBalance() khi Dev B hoàn thành.
 *   - Placeholder comment được đặt tại điểm gọi để Dev A không bị block.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRequestRepository bookingRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final WarehouseService warehouseService;
    private final ContractService contractService;
    private final SystemPolicyRepository systemPolicyRepository;
    private final WalletService walletService;
    private final SystemConfigService systemConfigService;
    // ==================== Tenant ====================
    /**
     * Tenant gửi yêu cầu thuê kho.
     *
     * Kiểm tra:
     * - Warehouse phải tồn tại và status = AVAILABLE
     * - Tenant chưa có booking PENDING cho kho này
     */
    @Transactional
    public BookingResponse sendBookingRequest(UUID tenantId, CreateBookingRequest request) {
        log.info("Tenant {} sending booking request for warehouse {}", tenantId, request.getWarehouseId());
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        // Kho phải AVAILABLE
        if (warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_NOT_AVAILABLE);
        }
        // Khóa giữ chỗ: Kiểm tra nếu kho đó đã có BookingRequest ở trạng thái PENDING hoặc APPROVED thì báo lỗi
        boolean hasExistingBooking = bookingRepository.existsByWarehouseIdAndStatusIn(
                warehouse.getId(), List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED));
        if (hasExistingBooking) {
            throw new BadRequestException("Kho bãi đã được đặt chỗ hoặc đang trong quá trình thỏa thuận hợp đồng");
        }
        // Không cho spam booking
        boolean hasPending = bookingRepository.existsByTenantIdAndWarehouseIdAndStatus(
                tenantId, warehouse.getId(), ApprovalStatus.PENDING);
        if (hasPending) {
            throw new BadRequestException(ErrorCode.BOOKING_DUPLICATE_PENDING);
        }
        SystemPolicy policy = systemPolicyRepository.findFirstByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chính sách/cam kết ràng buộc hiệu lực nào trong hệ thống"));
        
        // Lấy tỷ lệ cọc từ System_Config (mặc định 10%) tính toán số tiền cọc dựa trên giá kho pricePerMonth
        int depositPercent = systemConfigService.getIntValue("deposit_percentage", 10);
        BigDecimal depositPercentage = BigDecimal.valueOf(depositPercent);
        BigDecimal depositAmount = warehouse.getPricePerMonth()
                .multiply(depositPercentage)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

        BookingRequest booking = BookingRequest.builder()
                .tenant(tenant)
                .warehouse(warehouse)
                .depositAmount(depositAmount)
                .status(ApprovalStatus.PENDING)
                .policy(policy)
                .build();
        booking = bookingRepository.save(booking);

        // Trừ ngay tiền cọc từ ví Tenant
        walletService.deductBalance(
                tenantId,
                depositAmount,
                TransactionType.DEPOSIT_PAYMENT,
                "Đặt cọc thuê kho: " + warehouse.getName(),
                booking.getId(),
                null
        );

        log.info("Booking request created & deposit deducted: {} (tenant={}, warehouse={}, depositAmount={})",
                booking.getId(), tenantId, warehouse.getId(), depositAmount);
        return mapToResponse(booking);
    }
    /**
     * Tenant huỷ booking (chỉ khi status còn PENDING).
     */
    @Transactional
    public void cancelBooking(UUID tenantId, UUID bookingId) {
        BookingRequest booking = bookingRepository.findByIdAndTenantId(bookingId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND));
        if (booking.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.BOOKING_ALREADY_PROCESSED);
        }
        booking.setStatus(ApprovalStatus.REJECTED);
        booking.setRejectReason("Tenant tự huỷ yêu cầu");
        bookingRepository.save(booking);

        // Hoàn cọc 100% cho Tenant
        walletService.refundBalance(
                tenantId,
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn cọc do Tenant hủy yêu cầu đặt thuê kho: " + booking.getWarehouse().getName(),
                booking.getId(),
                null
        );

        log.info("Tenant {} cancelled booking {}", tenantId, bookingId);
    }
    /**
     * Tenant xem lịch sử booking của mình (phân trang).
     */
    @Transactional(readOnly = true)
    public PagedBookingResponse getMyBookings(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BookingRequest> bookingPage = bookingRepository.findByTenantId(tenantId, pageable);
        return toPagedResponse(bookingPage);
    }
    // ==================== Owner ====================
    /**
     * Owner xem danh sách yêu cầu thuê đến kho của mình (phân trang).
     */
    @Transactional(readOnly = true)
    public PagedBookingResponse getIncomingRequests(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BookingRequest> bookingPage = bookingRepository.findByWarehouseOwnerId(ownerId, pageable);
        return toPagedResponse(bookingPage);
    }
    /**
     * Owner chấp nhận yêu cầu thuê kho.
     *
     * Hành động:
     * 1. Đổi booking status → APPROVED
     * 2. [TODO - Dev B] Deduct deposit từ ví Tenant:
     *    walletService.deductBalance(booking.getTenant().getId(), booking.getDepositAmount(), "Đặt cọc thuê kho")
     * 3. Tạo RentalContract (gọi ContractService)
     * 4. Đổi Warehouse status → RENTED
     */
    @Transactional
    public BookingResponse approveBooking(UUID ownerId, UUID bookingId) {
        BookingRequest booking = getOwnerBooking(ownerId, bookingId);
        validatePending(booking);
        booking.setStatus(ApprovalStatus.APPROVED);
        booking = bookingRepository.save(booking);
        
        // Tạo RentalContract
        contractService.createContractFromBooking(booking.getId());
        // Đổi warehouse sang RENTED
        warehouseService.markAsRented(booking.getWarehouse().getId());
        log.info("Owner {} approved booking {} — warehouse {} is now RENTED",
                ownerId, bookingId, booking.getWarehouse().getId());
        return mapToResponse(booking);
    }
    /**
     * Owner từ chối yêu cầu thuê kho.
     */
    @Transactional
    public BookingResponse rejectBooking(UUID ownerId, UUID bookingId, String reason) {
        BookingRequest booking = getOwnerBooking(ownerId, bookingId);
        validatePending(booking);
        booking.setStatus(ApprovalStatus.REJECTED);
        booking.setRejectReason(reason);
        booking = bookingRepository.save(booking);

        // Hoàn cọc 100% cho Tenant
        walletService.refundBalance(
                booking.getTenant().getId(),
                booking.getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn cọc do Owner từ chối yêu cầu đặt thuê kho: " + booking.getWarehouse().getName(),
                booking.getId(),
                null
        );

        log.info("Owner {} rejected booking {} (reason: {})", ownerId, bookingId, reason);
        return mapToResponse(booking);
    }
    // ==================== Private helpers ====================
    private BookingRequest getOwnerBooking(UUID ownerId, UUID bookingId) {
        return bookingRepository.findByIdAndOwnerId(bookingId, ownerId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.BOOKING_NOT_FOUND));
    }
    private void validatePending(BookingRequest booking) {
        if (booking.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.BOOKING_ALREADY_PROCESSED);
        }
    }
    private BookingResponse mapToResponse(BookingRequest b) {
        User tenant = b.getTenant();
        Warehouse wh = b.getWarehouse();
        return BookingResponse.builder()
                .id(b.getId())
                .status(b.getStatus().name())
                .depositAmount(b.getDepositAmount())
                .rejectReason(b.getRejectReason())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantEmail(tenant != null ? tenant.getEmail() : null)
                .tenantPhone(tenant != null ? tenant.getPhone() : null)
                .warehouseId(wh != null ? wh.getId() : null)
                .warehouseName(wh != null ? wh.getName() : null)
                .warehouseAddress(wh != null ? wh.getAddress() : null)
                .ownerId(wh != null && wh.getOwner() != null ? wh.getOwner().getId() : null)
                .ownerName(wh != null && wh.getOwner() != null ? wh.getOwner().getFullName() : null)
                .policyId(b.getPolicy() != null ? b.getPolicy().getId() : null)
                .policyVersion(b.getPolicy() != null ? b.getPolicy().getVersion() : null)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
    private PagedBookingResponse toPagedResponse(Page<BookingRequest> page) {
        List<BookingResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return PagedBookingResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}