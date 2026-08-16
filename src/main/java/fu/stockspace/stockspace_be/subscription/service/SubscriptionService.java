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


            if (activePkg != null && activePkg.getId().equals(servicePackage.getId())) {
                log.info("Renewal package: Tenant {} renewing package '{}'", tenantId, servicePackage.getName());
                LocalDate newEndDate = activeSub.getEndDate().plusDays(servicePackage.getDurationDays());
                activeSub.setEndDate(newEndDate);

                activeSub.setSnapshotMaxStaff(servicePackage.getMaxStaff());
                activeSub.setSnapshotPrice(servicePackage.getPrice());
                activeSub.setSnapshotFeatures(servicePackage.getFeatures());
                activeSub.setSnapshotPackageName(servicePackage.getName());
                subscription = subscriptionRepository.save(activeSub);
            } else {

                java.math.BigDecimal currentPrice = activeSub.getSnapshotPrice() != null
                        ? activeSub.getSnapshotPrice()
                        : (activePkg != null ? activePkg.getPrice() : java.math.BigDecimal.ZERO);
                int currentMaxStaff = (activeSub.getSnapshotMaxStaff() != null && activeSub.getSnapshotMaxStaff() > 0)
                        ? activeSub.getSnapshotMaxStaff()
                        : (activePkg != null && activePkg.getMaxStaff() != null ? activePkg.getMaxStaff() : 0);

                boolean isLowerPrice = servicePackage.getPrice() != null && currentPrice != null ? servicePackage.getPrice().compareTo(currentPrice) < 0 : false;
                boolean isLowerStaff = (servicePackage.getMaxStaff() != null ? servicePackage.getMaxStaff() : 0) < currentMaxStaff;

                if (isLowerPrice || isLowerStaff) {
                    throw new BadRequestException("Không thể hạ xuống gói dịch vụ thấp hơn khi gói hiện tại vẫn đang còn hạn. Vui lòng hạ gói sau khi gói hiện tại kết thúc.");
                }


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


        tenantStaffService.deactivateExcessStaffs(tenantId, servicePackage.getMaxStaff());


        walletService.deductBalance(
                tenantId,
                servicePackage.getPrice(),
                TransactionType.PACKAGE_PAYMENT,
                "Thanh toán gói dịch vụ: " + servicePackage.getName(),
                null,
                subscription.getId()
        );


        final UUID subId = subscription.getId();
        userRepository.findFirstByRoles_Name(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_ADMIN.name())
                .ifPresent(adminUser -> {
                    try {
                        walletService.refundBalance(
                                adminUser.getId(),
                                servicePackage.getPrice(),
                                TransactionType.PACKAGE_PAYMENT,
                                "Doanh thu gói dịch vụ từ Tenant: " + servicePackage.getName(),
                                null,
                                subId
                        );
                    } catch (Exception e) {
                        log.error("Failed to credit system revenue to admin wallet: {}", e.getMessage(), e);
                    }
                });

        log.info("Subscription Service: Tenant {} processed package '{}' successfully. Subscription ID: {}",
                tenantId, servicePackage.getName(), subscription.getId());

        return mapToResponse(subscription);
    }




    @Transactional(readOnly = true)
    public SubscriptionResponse getMyActiveSubscription(UUID tenantId) {
        Subscription subscription = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        return mapToResponse(subscription);
    }



    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID tenantId) {
        return subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .isPresent();
    }




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
        int newMaxStaff = newPackage.getMaxStaff() != null ? newPackage.getMaxStaff() : 0;

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
        int currentMaxStaff = (activeSub.getSnapshotMaxStaff() != null && activeSub.getSnapshotMaxStaff() > 0)
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

        if (isLowerPrice || isLowerStaff) {
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
                .servicePackage(mapSubscriptionPackage(s))
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .status(s.getStatus())
                .build();
    }

    private fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse mapSubscriptionPackage(
            Subscription subscription) {
        fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse response =
                packageService.mapToResponse(subscription.getServicePackage());
        if (response == null) return null;

        if (subscription.getSnapshotPackageName() != null) {
            response.setName(subscription.getSnapshotPackageName());
        }
        if (subscription.getSnapshotFeatures() != null) {
            response.setFeatures(subscription.getSnapshotFeatures());
        }
        if (subscription.getSnapshotPrice() != null) {
            response.setPrice(subscription.getSnapshotPrice());
        }
        if (subscription.getSnapshotMaxStaff() != null) {
            response.setMaxStaff(subscription.getSnapshotMaxStaff());
        }
        return response;
    }

}
