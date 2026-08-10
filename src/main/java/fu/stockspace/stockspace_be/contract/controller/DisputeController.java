package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.CreateDisputeRequest;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.contract.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import fu.stockspace.stockspace_be.common.service.CloudinaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller xử lý các API Dispute Ticket.
 *
 * Endpoints:
 *   POST /api/disputes        — Mở tranh chấp
 *   GET  /api/disputes/mine   — Danh sách dispute của mình
 */
@Tag(name = "Dispute", description = "API quản lý tranh chấp hợp đồng")
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * POST /api/disputes
     * Mở tranh chấp cho hợp đồng (hỗ trợ upload ảnh bằng chứng).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.hasPermission('DISPUTE_CREATE')")
    @Operation(summary = "Mở tranh chấp hợp đồng")
    public ResponseEntity<ApiResponse<DisputeResponse>> raise(
            @Parameter(
                description = "Thông tin tranh chấp dạng JSON",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CreateDisputeRequest.class)
                )
            )
            @RequestPart("request") String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        java.util.UUID userId = getCurrentUser().getId();

        CreateDisputeRequest request;
        try {
            request = objectMapper.readValue(requestJson, CreateDisputeRequest.class);
        } catch (Exception e) {
            throw new BadRequestException("Định dạng JSON request không hợp lệ: " + e.getMessage());
        }

        Set<ConstraintViolation<CreateDisputeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String errorMsg = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Validation failed: " + errorMsg);
        }

        // Upload ảnh bằng chứng lên Cloudinary nếu có gửi file
        if (files != null && !files.isEmpty()) {
            List<String> urls = cloudinaryService.uploadImages(files);
            request.setEvidenceImages(urls);
        }

        DisputeResponse response = disputeService.raiseDispute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã mở tranh chấp thành công. Admin sẽ xử lý sớm.", response));
    }

    /**
     * GET /api/disputes/mine
     * Danh sách dispute do mình mở.
     */
    @GetMapping("/mine")
    @PreAuthorize("@rbac.hasPermission('DISPUTE_READ')")
    @Operation(summary = "Xem danh sách tranh chấp của mình")
    public ResponseEntity<ApiResponse<PagedResponse<DisputeResponse>>> getMyDisputes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.util.UUID userId = getCurrentUser().getId();
        Page<DisputeResponse> result = disputeService.getMyDisputes(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tranh chấp thành công", PagedResponse.fromPage(result)));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
