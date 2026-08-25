package fu.stockspace.stockspace_be.common.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Public — System Configurations", description = "Các API công khai xem cấu hình và biểu phí hệ thống")
@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class PublicSystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @Operation(summary = "Lấy danh sách cấu hình công khai của hợp đồng và kiểm định")
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> getPublicConfigs() {
        List<SystemConfigResponse> response = configService.getPublicConfigs();
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình hệ thống thành công", response));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Lấy cấu hình công khai chi tiết theo key")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getPublicConfigByKey(@PathVariable String key) {
        SystemConfigResponse response = configService.getPublicConfigByKey(key);
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình hệ thống thành công", response));
    }
}
