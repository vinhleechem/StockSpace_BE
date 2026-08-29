package fu.stockspace.stockspace_be.admin.controller;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.subscription.dto.CreatePackageRequest;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.dto.UpdatePackageRequest;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin — Packages & Subscriptions", description = "Các API quản lý gói dịch vụ và theo dõi lịch sử đăng ký dành cho Admin")
@PreAuthorize("@rbac.hasPermission('ADMIN_PACKAGE_MANAGE')")
public class AdminPackageController {
    private final ServicePackageService packageService;
    private final SubscriptionService subscriptionService;
    @PostMapping("/packages")
    @Operation(summary = "Tạo mới một gói dịch vụ thành viên")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> createPackage(
            @Valid @RequestBody CreatePackageRequest request) {
        ServicePackageResponse response = packageService.createPackage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo gói dịch vụ thành công", response));
    }
    @PutMapping("/packages/{id}")
    @Operation(summary = "Cập nhật thông tin một gói dịch vụ")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> updatePackage(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody UpdatePackageRequest request) {
        ServicePackageResponse response = packageService.updatePackage(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật gói dịch vụ thành công", response));
    }
    @DeleteMapping("/packages/{id}")
    @Operation(summary = "Xóa (Ngừng cung cấp) một gói dịch vụ")
    public ResponseEntity<ApiResponse<Void>> deletePackage(@PathVariable java.util.UUID id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa gói dịch vụ thành công", null));
    }
    @GetMapping("/subscriptions")
    @Operation(summary = "Xem lịch sử mua gói của tất cả Tenant (phân trang)")
    public ResponseEntity<ApiResponse<Page<SubscriptionResponse>>> getAllSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SubscriptionResponse> response = subscriptionService.getAllSubscriptions(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đăng ký gói thành công", response));
    }
}
