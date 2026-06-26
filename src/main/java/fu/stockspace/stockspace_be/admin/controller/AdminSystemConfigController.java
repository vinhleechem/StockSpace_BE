package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.UpdateSystemConfigRequest;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — System Configurations Management", description = "Các API quản lý cấu hình và biểu phí hệ thống của Admin")
@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các cấu hình và phí hiện tại (Admin)")
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> getAllConfigs() {
        List<SystemConfigResponse> response = configService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách cấu hình hệ thống thành công", response));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Cập nhật giá trị cấu hình hệ thống theo key (Admin)")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemConfigRequest request
    ) {
        SystemConfigResponse response = configService.updateConfig(key, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cấu hình hệ thống thành công", response));
    }
}
