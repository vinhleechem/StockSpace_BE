package fu.stockspace.stockspace_be.subscription.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
import fu.stockspace.stockspace_be.subscription.dto.PurchasePackageRequest;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ServicePackageRepository packageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private ServicePackageService packageService;

    @Mock
    private TenantStaffService tenantStaffService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private UUID tenantId;
    private UUID packageBasicId;
    private UUID packageProId;
    private User tenantUser;
    private ServicePackage packageBasic;
    private ServicePackage packagePro;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        packageBasicId = UUID.randomUUID();
        packageProId = UUID.randomUUID();

        tenantUser = User.builder()
                .id(tenantId)
                .email("tenant@stockspace.com")
                .fullName("Tenant Enterprise")
                .build();

        packageBasic = ServicePackage.builder()
                .id(packageBasicId)
                .name("Gói Cơ Bản")
                .price(new BigDecimal("300000.00"))
                .durationDays(30)
                .maxStaff(3)
                .features("[\"Quản lý XNT\", \"Báo cáo kho\"]")
                .isActive(true)
                .build();

        packagePro = ServicePackage.builder()
                .id(packageProId)
                .name("Gói Chuyên Nghiệp")
                .price(new BigDecimal("1000000.00"))
                .durationDays(30)
                .maxStaff(10)
                .features("[\"Full tính năng WMS\", \"AI Chatbot\"]")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("1. Mua mới gói dịch vụ thành công - Tạo Subscription và lưu ảnh chụp (Snapshot)")
    void purchasePackage_NewPurchase_Success() {
        PurchasePackageRequest request = new PurchasePackageRequest(packageBasicId);

        when(packageRepository.findById(packageBasicId)).thenReturn(Optional.of(packageBasic));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription sub = invocation.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        when(packageService.mapToResponse(packageBasic)).thenReturn(
                ServicePackageResponse.builder()
                        .id(packageBasicId)
                        .name(packageBasic.getName())
                        .price(packageBasic.getPrice())
                        .durationDays(packageBasic.getDurationDays())
                        .maxStaff(packageBasic.getMaxStaff())
                        .build()
        );

        SubscriptionResponse response = subscriptionService.purchasePackage(tenantId, request);

        assertNotNull(response);
        assertEquals(packageBasicId, response.getServicePackage().getId());
        assertEquals(SubscriptionStatus.ACTIVE, response.getStatus());

        verify(walletService, times(1)).deductBalance(
                eq(tenantId), eq(packageBasic.getPrice()), eq(TransactionType.PACKAGE_PAYMENT),
                contains("Gói Cơ Bản"), any(), any()
        );
        verify(tenantStaffService, times(1)).deactivateExcessStaffs(tenantId, 3);
    }

    @Test
    @DisplayName("2. Gia hạn gói đang dùng thành công - Nối tiếp thời hạn endDate và cập nhật Snapshot")
    void purchasePackage_Renewal_Success() {
        PurchasePackageRequest request = new PurchasePackageRequest(packageBasicId);

        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .servicePackage(packageBasic)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .status(SubscriptionStatus.ACTIVE)
                .snapshotMaxStaff(3)
                .snapshotPrice(packageBasic.getPrice())
                .snapshotPackageName("Gói Cơ Bản")
                .build();

        when(packageRepository.findById(packageBasicId)).thenReturn(Optional.of(packageBasic));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSub));

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageService.mapToResponse(packageBasic)).thenReturn(
                ServicePackageResponse.builder().id(packageBasicId).name("Gói Cơ Bản").build()
        );

        SubscriptionResponse response = subscriptionService.purchasePackage(tenantId, request);

        assertNotNull(response);
        assertEquals(LocalDate.now().plusDays(50), activeSub.getEndDate());
        verify(walletService, times(1)).deductBalance(eq(tenantId), eq(packageBasic.getPrice()), any(), any(), any(), any());
    }

    @Test
    @DisplayName("3. Nâng cấp gói thành công - Ngắt gói cũ thành SUPERSEDED và kích hoạt gói mới ngay lập tức")
    void purchasePackage_Upgrade_Success() {
        PurchasePackageRequest request = new PurchasePackageRequest(packageProId);

        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .servicePackage(packageBasic)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(SubscriptionStatus.ACTIVE)
                .snapshotMaxStaff(3)
                .snapshotPrice(packageBasic.getPrice())
                .snapshotPackageName("Gói Cơ Bản")
                .build();

        when(packageRepository.findById(packageProId)).thenReturn(Optional.of(packagePro));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSub));

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription sub = invocation.getArgument(0);
            if (sub.getId() == null) sub.setId(UUID.randomUUID());
            return sub;
        });

        when(packageService.mapToResponse(packagePro)).thenReturn(
                ServicePackageResponse.builder().id(packageProId).name("Gói Chuyên Nghiệp").build()
        );

        SubscriptionResponse response = subscriptionService.purchasePackage(tenantId, request);

        assertNotNull(response);
        assertEquals(SubscriptionStatus.SUPERSEDED, activeSub.getStatus());
        assertEquals(LocalDate.now(), activeSub.getEndDate());
        verify(tenantStaffService, times(1)).deactivateExcessStaffs(tenantId, 10);
    }

    @Test
    @DisplayName("4. Hạ cấp gói bị chặn giữa chu kỳ - Báo lỗi BadRequestException")
    void purchasePackage_Downgrade_Blocked() {
        PurchasePackageRequest request = new PurchasePackageRequest(packageBasicId);

        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .servicePackage(packagePro)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(SubscriptionStatus.ACTIVE)
                .snapshotMaxStaff(10)
                .snapshotPrice(packagePro.getPrice())
                .snapshotPackageName("Gói Chuyên Nghiệp")
                .build();

        when(packageRepository.findById(packageBasicId)).thenReturn(Optional.of(packageBasic));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSub));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                subscriptionService.purchasePackage(tenantId, request)
        );

        assertTrue(ex.getMessage().contains("Không thể hạ xuống gói dịch vụ thấp hơn"));
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("5. Preview chuyển đổi gói - Trả về kết quả chính xác")
    void previewSubscriptionChange_Scenarios() {
        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .servicePackage(packageBasic)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(SubscriptionStatus.ACTIVE)
                .snapshotMaxStaff(3)
                .snapshotPrice(packageBasic.getPrice())
                .snapshotPackageName("Gói Cơ Bản")
                .build();

        when(packageRepository.findById(packageProId)).thenReturn(Optional.of(packagePro));
        when(packageRepository.findById(packageBasicId)).thenReturn(Optional.of(packageBasic));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSub));

        // Case Upgrade
        SubscriptionPreviewResponse previewUpgrade = subscriptionService.previewSubscriptionChange(tenantId, packageProId);
        assertEquals("UPGRADE", previewUpgrade.getTransactionType());
        assertTrue(previewUpgrade.isCanProceed());

        // Case Renewal
        SubscriptionPreviewResponse previewRenewal = subscriptionService.previewSubscriptionChange(tenantId, packageBasicId);
        assertEquals("RENEWAL", previewRenewal.getTransactionType());
        assertTrue(previewRenewal.isCanProceed());
    }
}
