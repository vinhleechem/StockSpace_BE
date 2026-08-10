package fu.stockspace.stockspace_be.subscription.controller;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.subscription.dto.PurchasePackageRequest;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@RequestMapping("/api/tenant/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Tenant — Subscriptions", description = "Các API đăng ký gói dịch vụ dành cho Tenant")
@PreAuthorize("@rbac.hasPermission('PACKAGE_PURCHASE')")
public class TenantSubscriptionController {
    private final SubscriptionService subscriptionService;
    @PostMapping
    @Operation(summary = "Đăng ký mua gói dịch vụ")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> purchasePackage(
            @Valid @RequestBody PurchasePackageRequest request) {
        User user = getCurrentUser();
        SubscriptionResponse response = subscriptionService.purchasePackage(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mua gói dịch vụ thành công", response));
    }
    @GetMapping("/active")
    @Operation(summary = "Xem thông tin gói dịch vụ đang hoạt động")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMyActiveSubscription() {
        User user = getCurrentUser();
        SubscriptionResponse response = subscriptionService.getMyActiveSubscription(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin gói đang hoạt động thành công", response));
    }
    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
