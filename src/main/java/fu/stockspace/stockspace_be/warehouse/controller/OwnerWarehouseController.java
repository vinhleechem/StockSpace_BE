package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import fu.stockspace.stockspace_be.common.service.CloudinaryService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;













@Tag(name = "Owner — Warehouse Management", description = "Quản lý kho bãi của Owner")
@RestController
@RequestMapping("/api/owner/warehouses")
@RequiredArgsConstructor
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Warehouse is not owned by the caller",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"WAREHOUSE_NOT_OWNED\",\"message\":\"You are not the warehouse owner\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Warehouse not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"WAREHOUSE_NOT_FOUND\",\"message\":\"Warehouse not found\"}")))
})
public class OwnerWarehouseController {

    private final WarehouseService warehouseService;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;








    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_CREATE')")
    @Operation(summary = "Tạo warehouse mới (Owner)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(
            @Parameter(
                description = "Thông tin kho dạng JSON",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreateWarehouseRequest.class)
                )
            )
            @RequestPart("request") String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        UUID ownerId = getCurrentUserId();

        CreateWarehouseRequest request;
        try {
            request = objectMapper.readValue(requestJson, CreateWarehouseRequest.class);
        } catch (Exception e) {
            throw new BadRequestException("Định dạng JSON request không hợp lệ: " + e.getMessage());
        }

        Set<ConstraintViolation<CreateWarehouseRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errorMsg = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Validation failed: " + errorMsg);
        }

        if (files != null && !files.isEmpty()) {
            List<String> urls = cloudinaryService.uploadImages(files);
            request.setImageUrls(urls);
        }
        WarehouseResponse response = warehouseService.createWarehouse(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo kho thành công. Đang chờ Admin xét duyệt.", response));
    }







    @GetMapping
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_READ')")
    @Operation(summary = "Danh sách kho của Owner (phân trang)")
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseResponse>>> getMyWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        UUID ownerId = getCurrentUserId();
        PagedResponse<WarehouseResponse> result = warehouseService.getMyWarehouses(ownerId, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }







    @PutMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
    @Operation(summary = "Cập nhật thông tin warehouse (Owner)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        UUID ownerId = getCurrentUserId();
        WarehouseResponse response = warehouseService.updateWarehouse(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật kho thành công", response));
    }








    @PatchMapping("/{id}/status")
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
    @Operation(summary = "Cập nhật trạng thái warehouse (AVAILABLE / INACTIVE)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam WarehouseStatus status
    ) {
        UUID ownerId = getCurrentUserId();
        WarehouseResponse response = warehouseService.updateStatus(ownerId, id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái kho thành công", response));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
    @Operation(summary = "Gửi lại bài đăng warehouse sau khi bị từ chối")
    public ResponseEntity<ApiResponse<WarehouseResponse>> resubmit(@PathVariable UUID id) {
        UUID ownerId = getCurrentUserId();
        WarehouseResponse response = warehouseService.resubmitWarehouse(ownerId, id);
        return ResponseEntity.ok(ApiResponse.success(
                "Gửi lại bài đăng thành công. Vui lòng chọn gói và thanh toán lại.", response));
    }







    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_DELETE')")
    @Operation(summary = "Xoá warehouse (Owner)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        UUID ownerId = getCurrentUserId();
        warehouseService.deleteWarehouse(ownerId, id);
        return ResponseEntity.ok(ApiResponse.success("Xoá kho thành công", null));
    }








    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
    @Operation(summary = "Thêm ảnh vào warehouse")
    public ResponseEntity<ApiResponse<List<String>>> addImages(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        UUID ownerId = getCurrentUserId();
        List<String> urls = cloudinaryService.uploadImages(files);
        List<String> saved = warehouseService.addImages(ownerId, id, urls);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm ảnh thành công", saved));
    }





    @PutMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
    @Operation(summary = "Thay thế toàn bộ ảnh warehouse")
    public ResponseEntity<ApiResponse<List<String>>> replaceImages(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files
    ) throws IOException {
        UUID ownerId = getCurrentUserId();
        List<String> urls = cloudinaryService.uploadImages(files);
        List<String> saved = warehouseService.replaceImages(ownerId, id, urls);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh thành công", saved));
    }



    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
