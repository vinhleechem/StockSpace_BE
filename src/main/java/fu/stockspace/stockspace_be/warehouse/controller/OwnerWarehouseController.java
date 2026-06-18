package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

/**
 * Controller xử lý các API Quản lý Kho của Warehouse Owner.
 *
 * Endpoints:
 *   POST   /api/owner/warehouses                       — Tạo kho mới
 *   PUT    /api/owner/warehouses/{id}                  — Cập nhật thông tin kho
 *   DELETE /api/owner/warehouses/{id}                  — Xoá kho
 *   PATCH  /api/owner/warehouses/{id}/status           — Đổi trạng thái (AVAILABLE/INACTIVE)
 *   GET    /api/owner/warehouses                       — Danh sách kho của mình (phân trang)
 *   POST   /api/owner/warehouses/{id}/images           — Thêm ảnh
 *   PUT    /api/owner/warehouses/{id}/images           — Thay thế toàn bộ ảnh
 */
@Tag(name = "Owner — Warehouse Management", description = "Quản lý kho bãi của Owner")
@RestController
@RequestMapping("/api/owner/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class OwnerWarehouseController {

    private final WarehouseService warehouseService;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // ==================== Create ====================

    /**
     * POST /api/owner/warehouses
     * Tạo mới một Warehouse listing.
     * Status ban đầu sẽ là PENDING_APPROVAL.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    // ==================== Read ====================

    /**
     * GET /api/owner/warehouses
     * Danh sách kho của Owner hiện tại, có phân trang và sắp xếp.
     */
    @GetMapping
    @Operation(summary = "Danh sách kho của Owner (phân trang)")
    public ResponseEntity<ApiResponse<PagedWarehouseResponse>> getMyWarehouses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        UUID ownerId = getCurrentUserId();
        PagedWarehouseResponse result = warehouseService.getMyWarehouses(ownerId, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }

    // ==================== Update ====================

    /**
     * PUT /api/owner/warehouses/{id}
     * Cập nhật thông tin kho (name, address, description, price, capacity, type).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin warehouse (Owner)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        UUID ownerId = getCurrentUserId();
        WarehouseResponse response = warehouseService.updateWarehouse(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật kho thành công", response));
    }

    // ==================== Status ====================

    /**
     * PATCH /api/owner/warehouses/{id}/status?status=INACTIVE
     * Owner tắt/mở listing (AVAILABLE hoặc INACTIVE).
     * Không thể tự set RENTED.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái warehouse (AVAILABLE / INACTIVE)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestParam WarehouseStatus status
    ) {
        UUID ownerId = getCurrentUserId();
        WarehouseResponse response = warehouseService.updateStatus(ownerId, id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái kho thành công", response));
    }

    // ==================== Delete ====================

    /**
     * DELETE /api/owner/warehouses/{id}
     * Xoá warehouse. Không xoá được khi đang có Tenant thuê.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá warehouse (Owner)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        UUID ownerId = getCurrentUserId();
        warehouseService.deleteWarehouse(ownerId, id);
        return ResponseEntity.ok(ApiResponse.success("Xoá kho thành công", null));
    }

    // ==================== Images ====================

    /**
     * POST /api/owner/warehouses/{id}/images
     * Thêm ảnh vào warehouse (tối đa 10 ảnh).
     * Body: { "imageUrls": ["url1", "url2"] }
     */
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    /**
     * PUT /api/owner/warehouses/{id}/images
     * Thay thế toàn bộ ảnh bằng danh sách mới.
     */
    @PutMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    // ==================== Private helpers ====================

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
