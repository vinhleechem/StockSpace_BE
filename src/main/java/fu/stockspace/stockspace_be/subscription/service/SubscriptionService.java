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
    /**
     * Mua gói dịch vụ. Khấu trừ tiền ví Tenant và kích hoạt gói.
     */
    @Transactional
    public SubscriptionResponse purchasePackage(UUID tenantId, PurchasePackageRequest request) {
        ServicePackage servicePackage = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        if (!servicePackage.isActive()) {
            throw new BadRequestException("Gói dịch vụ này hiện đã ngừng cung cấp");
        }
        // 1. Kiểm tra gói active hiện tại. Nếu đang có gói active -> Chặn mua (theo error code SUBSCRIPTION_ALREADY_ACTIVE)
        boolean hasActive = hasActiveSubscription(tenantId);
        if (hasActive) {
            throw new BadRequestException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
        }
        // 2. Kích hoạt Subscription mới (Lưu trước để Hibernate tự generate ID)
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(servicePackage.getDurationDays());

        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .servicePackage(servicePackage)
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        subscription = subscriptionRepository.save(subscription);

        // 3. Trừ tiền ví (Truyền ID của gói vừa tạo vào reference)
        walletService.deductBalance(
                tenantId,
                servicePackage.getPrice(),
                TransactionType.PACKAGE_PAYMENT,
                "Mua gói dịch vụ: " + servicePackage.getName(),
                null,
                subscription.getId()
        );

        log.info("Subscription Service: Tenant {} purchased package '{}' successfully. Subscription ID: {}", 
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
     * Admin xem tất cả các lượt đăng ký gói.
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> getAllSubscriptions(Pageable pageable) {
        return subscriptionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }
    private SubscriptionResponse mapToResponse(Subscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId())
                .tenantId(s.getTenant().getId())
                .servicePackage(packageService.mapToResponse(s.getServicePackage()))
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .status(s.getStatus())
                .build();
    }
}