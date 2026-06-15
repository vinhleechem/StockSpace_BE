package fu.stockspace.stockspace_be.booking.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.booking.dto.*;
import fu.stockspace.stockspace_be.booking.service.BookingService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý các API Booking của Tenant.
 *
 * Endpoints:
 *   POST   /api/tenant/bookings       — Gửi yêu cầu thuê kho
 *   GET    /api/tenant/bookings       — Xem lịch sử booking (phân trang)
 *   DELETE /api/tenant/bookings/{id}  — Huỷ booking (chỉ khi PENDING)
 */
@Tag(name = "Tenant — Booking", description = "API gửi và quản lý yêu cầu thuê kho của Tenant")
@RestController
@RequestMapping("/api/tenant/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT')")
public class TenantBookingController {

    private final BookingService bookingService;

    /**
     * POST /api/tenant/bookings
     * Gửi yêu cầu thuê kho.
     */
    @PostMapping
    @Operation(summary = "Gửi yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<BookingResponse>> sendRequest(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        Long tenantId = getCurrentUserId();
        BookingResponse response = bookingService.sendBookingRequest(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi yêu cầu thuê kho thành công. Đang chờ Owner xét duyệt.", response));
    }

    /**
     * GET /api/tenant/bookings
     * Danh sách booking của Tenant, phân trang.
     */
    @GetMapping
    @Operation(summary = "Xem lịch sử yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<PagedBookingResponse>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long tenantId = getCurrentUserId();
        PagedBookingResponse result = bookingService.getMyBookings(tenantId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách booking thành công", result));
    }

    /**
     * DELETE /api/tenant/bookings/{id}
     * Tenant tự huỷ yêu cầu (chỉ được khi status PENDING).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Huỷ yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        Long tenantId = getCurrentUserId();
        bookingService.cancelBooking(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Huỷ yêu cầu thuê kho thành công", null));
    }

    private Long getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
