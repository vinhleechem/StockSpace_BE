package fu.stockspace.stockspace_be.common.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.service.SystemPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;




@Tag(name = "Public — System Policies", description = "Các API công khai liên quan đến chính sách/cam kết ràng buộc")
@RestController
@RequestMapping("/api/system-policies")
@RequiredArgsConstructor
public class PublicSystemPolicyController {

    private final SystemPolicyService systemPolicyService;





    @GetMapping("/active")
    @Operation(summary = "Xem cam kết ràng buộc đang hiệu lực mới nhất")
    public ResponseEntity<ApiResponse<SystemPolicyResponse>> getActivePolicy() {
        SystemPolicyResponse response = systemPolicyService.getActivePolicy();
        return ResponseEntity.ok(ApiResponse.success("Lấy chính sách đang hiệu lực thành công", response));
    }
}
