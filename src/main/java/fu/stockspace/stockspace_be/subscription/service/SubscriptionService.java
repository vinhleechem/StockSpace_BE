package fu.stockspace.stockspace_be.subscription.service;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.subscription.dto.PurchasePackageRequest;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final ServicePackageRepository packageRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ServicePackageService packageService;
    private final fu.stockspace.stockspace_be.staff.service.TenantStaffService tenantStaffService;
    /**
     * Mua gói dịch vụ. Khấu trừ tiền ví Tenant và kích hoạt gói.
     * Hỗ trợ 3 trường hợp:
     *  1. Gia hạn (Renewal - Mua cùng gói cũ): Nối tiếp thời hạn endDate += durationDays.
     *  2. Nâng cấp (Upgrade - Mua gói mới bằng hoặc cao hơn): Kích hoạt ngay gói mới, ngắt gói cũ (SUPERSEDED).
     *  3. Hạ cấp (Downgrade - Mua gói thấp hơn khi gói cũ còn hạn): Chặn và báo lỗi.
     */
    @Transactional
    public SubscriptionResponse purchasePackage(UUID tenantId, PurchasePackageRequest request) {
        ServicePackage servicePackage = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        if (!servicePackage.isActive()) {
            throw new BadRequestException("Gói dịch vụ này hiện đã ngừng cung cấp");
        }

        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now());

        Subscription subscription;

        if (activeOpt.isPresent()) {
            Subscription activeSub = activeOpt.get();
            ServicePackage activePkg = activeSub.getServicePackage();

            // 1. Gia hạn (Same package)
            if (activePkg != null && activePkg.getId().equals(servicePackage.getId())) {
                log.info("Renewal package: Tenant {} renewing package '{}'", tenantId, servicePackage.getName());
                LocalDate newEndDate = activeSub.getEndDate().plusDays(servicePackage.getDurationDays());
                activeSub.setEndDate(newEndDate);
                // Cập nhật snapshot nếu gói đã được Admin điều chỉnh
                activeSub.setSnapshotMaxStaff(servicePackage.getMaxStaff());
                activeSub.setSnapshotPrice(servicePackage.getPrice());
                activeSub.setSnapshotFeatures(servicePackage.getFeatures());
                activeSub.setSnapshotPackageName(servicePackage.getName());
                subscription = subscriptionRepository.save(activeSub);
            } else {
                // Kiểm tra Hạ cấp (Downgrade Check)
                java.math.BigDecimal currentPrice = activeSub.getSnapshotPrice() != null
                        ? activeSub.getSnapshotPrice()
                        : (activePkg != null ? activePkg.getPrice() : java.math.BigDecimal.ZERO);
                int currentMaxStaff = activeSub.getSnapshotMaxStaff() > 0
                        ? activeSub.getSnapshotMaxStaff()
                        : (activePkg != null ? activePkg.getMaxStaff() : 0);

                boolean isLowerPrice = servicePackage.getPrice().compareTo(currentPrice) < 0;
                boolean isLowerStaff = servicePackage.getMaxStaff() < currentMaxStaff;

                if (isLowerPrice && isLowerStaff) {
                    throw new BadRequestException("Không thể hạ xuống gói dịch vụ thấp hơn khi gói hiện tại vẫn đang còn hạn. Vui lòng hạ gói sau khi gói hiện tại kết thúc.");
                }

                // 2. Nâng cấp (Upgrade): Ngắt gói cũ thành SUPERSEDED và tạo gói mới
                String oldPkgName = activeSub.getSnapshotPackageName() != null
                        ? activeSub.getSnapshotPackageName()
                        : (activePkg != null ? activePkg.getName() : "Gói hiện tại");
                log.info("Upgrade package: Tenant {} upgrading from '{}' to '{}'",
                        tenantId, oldPkgName, servicePackage.getName());

                activeSub.setStatus(SubscriptionStatus.SUPERSEDED);
                activeSub.setEndDate(LocalDate.now());
                subscriptionRepository.save(activeSub);


                LocalDate startDate = LocalDate.now();
                LocalDate endDate = startDate.plusDays(servicePackage.getDurationDays());

                subscription = Subscription.builder()
                        .tenant(tenant)
                        .servicePackage(servicePackage)
                        .startDate(startDate)
                        .endDate(endDate)
                        .status(SubscriptionStatus.ACTIVE)
                        .snapshotMaxStaff(servicePackage.getMaxStaff())
                        .snapshotPrice(servicePackage.getPrice())
                        .snapshotFeatures(servicePackage.getFeatures())
                        .snapshotPackageName(servicePackage.getName())
                        .build();
                subscription = subscriptionRepository.save(subscription);
            }
        } else {
            // 3. Mua mới hoàn toàn
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = startDate.plusDays(servicePackage.getDurationDays());

            subscription = Subscription.builder()
                    .tenant(tenant)
                    .servicePackage(servicePackage)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(SubscriptionStatus.ACTIVE)
                    .snapshotMaxStaff(servicePackage.getMaxStaff())
                    .snapshotPrice(servicePackage.getPrice())
                    .snapshotFeatures(servicePackage.getFeatures())
                    .snapshotPackageName(servicePackage.getName())
                    .build();
            subscription = subscriptionRepository.save(subscription);
        }

        // Tự động kiểm tra và khóa bớt Staff nếu vượt quota gói mới mua
        tenantStaffService.deactivateExcessStaffs(tenantId, servicePackage.getMaxStaff());

        // Trừ tiền ví Tenant
        walletService.deductBalance(
                tenantId,
                servicePackage.getPrice(),
                TransactionType.PACKAGE_PAYMENT,
                "Thanh toán gói dịch vụ: " + servicePackage.getName(),
                null,
                subscription.getId()
        );

        log.info("Subscription Service: Tenant {} processed package '{}' successfully. Subscription ID: {}",
                tenantId, servicePackage.getName(), subscription.getId());

        return mapToResponse(subscription);
    }

    /**
     * Lấy thông tin gói dịch vụ đang active của Tenant.
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getMyActiveSubscription(UUID tenantId) {
        Subscription subscription = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        return mapToResponse(subscription);
    }
    /**
     * Helper kiểm tra nhanh xem Tenant có gói active không.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID tenantId) {
        return subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .isPresent();
    }

    /**
     * Preview chuyển đổi gói dịch vụ cho Tenant xem trước khi xác nhận bấm mua.
     */
    @Transactional(readOnly = true)
    public fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse previewSubscriptionChange(UUID tenantId, UUID newPackageId) {

        if (newPackageId == null) {
            throw new BadRequestException("ID gói dịch vụ không được để trống");
        }

        ServicePackage newPackage = packageRepository.findById(newPackageId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));

        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now());

        java.math.BigDecimal newPrice = newPackage.getPrice() != null ? newPackage.getPrice() : java.math.BigDecimal.ZERO;
        int newMaxStaff = newPackage.getMaxStaff();

        if (activeOpt.isEmpty()) {
            return fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse.builder()
                    .newPackageId(newPackage.getId())
                    .newPackageName(newPackage.getName())
                    .newMaxStaff(newMaxStaff)
                    .newPrice(newPrice)
                    .transactionType("NEW_PURCHASE")
                    .canProceed(true)
                    .message("Đăng ký mới gói dịch vụ " + (newPackage.getName() != null ? newPackage.getName() : ""))
                    .build();
        }

        Subscription activeSub = activeOpt.get();
        ServicePackage currentPackage = activeSub.getServicePackage();

        UUID currentPackageId = currentPackage != null ? currentPackage.getId() : null;
        String currentPackageName = activeSub.getSnapshotPackageName() != null
                ? activeSub.getSnapshotPackageName()
                : (currentPackage != null ? currentPackage.getName() : "Gói hiện tại");
        java.math.BigDecimal currentPrice = activeSub.getSnapshotPrice() != null
                ? activeSub.getSnapshotPrice()
                : (currentPackage != null && currentPackage.getPrice() != null ? currentPackage.getPrice() : java.math.BigDecimal.ZERO);
        int currentMaxStaff = activeSub.getSnapshotMaxStaff() > 0
                ? activeSub.getSnapshotMaxStaff()
                : (currentPackage != null ? currentPackage.getMaxStaff() : 0);

        if (currentPackageId != null && currentPackageId.equals(newPackageId)) {
            return fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse.builder()
                    .currentPackageId(currentPackageId)
                    .currentPackageName(currentPackageName)
                    .currentMaxStaff(currentMaxStaff)
                    .currentPrice(currentPrice)
                    .newPackageId(newPackage.getId())
                    .newPackageName(newPackage.getName())
                    .newMaxStaff(newMaxStaff)
                    .newPrice(newPrice)
                    .transactionType("RENEWAL")
                    .canProceed(true)
                    .message("Gia hạn thêm " + (newPackage.getDurationDays() != null ? newPackage.getDurationDays() : 0) + " ngày cho gói " + (newPackage.getName() != null ? newPackage.getName() : ""))
                    .build();
        }

        boolean isLowerPrice = newPrice.compareTo(currentPrice) < 0;
        boolean isLowerStaff = newMaxStaff < currentMaxStaff;

        if (isLowerPrice && isLowerStaff) {
            return fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse.builder()
                    .currentPackageId(currentPackageId)
                    .currentPackageName(currentPackageName)
                    .currentMaxStaff(currentMaxStaff)
                    .currentPrice(currentPrice)
                    .newPackageId(newPackage.getId())
                    .newPackageName(newPackage.getName())
                    .newMaxStaff(newMaxStaff)
                    .newPrice(newPrice)
                    .transactionType("DOWNGRADE_BLOCKED")
                    .canProceed(false)
                    .message("Không thể hạ xuống gói thấp hơn khi gói hiện tại vẫn đang còn hạn. Bạn chỉ có thể đăng ký gói mới sau khi gói hiện tại hết hạn.")
                    .build();
        }

        return fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse.builder()
                .currentPackageId(currentPackageId)
                .currentPackageName(currentPackageName)
                .currentMaxStaff(currentMaxStaff)
                .currentPrice(currentPrice)
                .newPackageId(newPackage.getId())
                .newPackageName(newPackage.getName())
                .newMaxStaff(newMaxStaff)
                .newPrice(newPrice)
                .transactionType("UPGRADE")
                .canProceed(true)
                .message("Nâng cấp từ gói " + currentPackageName + " lên gói " + (newPackage.getName() != null ? newPackage.getName() : "") + ". Gói mới có hiệu lực ngay lập tức.")
                .build();
    }


    /**
     * Admin xem tất cả các lượt đăng ký gói.
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getAllSubscriptions(Pageable pageable) {

        return subscriptionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }
    private SubscriptionResponse mapToResponse(Subscription s) {
        if (s == null) return null;
        return SubscriptionResponse.builder()
                .id(s.getId())
                .tenantId(s.getTenant() != null ? s.getTenant().getId() : null)
                .servicePackage(packageService.mapToResponse(s.getServicePackage()))
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .status(s.getStatus())
                .build();
    }

}