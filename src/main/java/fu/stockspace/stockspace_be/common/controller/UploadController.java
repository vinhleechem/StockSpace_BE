package fu.stockspace.stockspace_be.common.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Controller xử lý các yêu cầu upload tài nguyên hình ảnh.
 */
@Tag(name = "Upload — Media Upload Controller", description = "Các API phục vụ cho việc upload hình ảnh lên Cloudinary")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final CloudinaryService cloudinaryService;

    /**
     * POST /api/upload/image
     * Upload single image.
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload một hình ảnh (Yêu cầu đăng nhập)")
    public ResponseEntity<ApiResponse<String>> uploadSingleImage(
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn file để upload"));
        }
        try {
            String url = cloudinaryService.uploadImage(file);
            return ResponseEntity.ok(ApiResponse.success("Upload ảnh thành công", url));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Lỗi khi upload ảnh lên Cloudinary: " + e.getMessage()));
        }
    }

    /**
     * POST /api/upload/images
     * Upload multiple images.
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload nhiều hình ảnh cùng lúc (Yêu cầu đăng nhập)")
    public ResponseEntity<ApiResponse<List<String>>> uploadMultipleImages(
            @RequestParam("files") List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn ít nhất một file để upload"));
        }
        try {
            List<String> urls = cloudinaryService.uploadImages(files);
            return ResponseEntity.ok(ApiResponse.success("Upload danh sách ảnh thành công", urls));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Lỗi khi upload danh sách ảnh lên Cloudinary: " + e.getMessage()));
        }
    }
}
