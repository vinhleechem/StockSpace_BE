package fu.stockspace.stockspace_be.subscription.controller;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
@Tag(name = "Public — Packages", description = "Các API xem thông tin gói dịch vụ")
@PreAuthorize("isAuthenticated()")
public class PublicPackageController {
    private final ServicePackageService packageService;
    @GetMapping
    @Operation(summary = "Xem danh sách các gói dịch vụ")
    public ResponseEntity<ApiResponse<List<ServicePackageResponse>>> getAllPackages() {
        List<ServicePackageResponse> response = packageService.getAllPackages();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gói dịch vụ thành công", response));
    }
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một gói dịch vụ")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> getPackageById(@PathVariable Integer id) {
        ServicePackageResponse response = packageService.getPackageById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết gói dịch vụ thành công", response));
    }
}