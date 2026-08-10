package fu.stockspace.stockspace_be.booking.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.booking.dto.*;
import fu.stockspace.stockspace_be.booking.service.BookingService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



/**
 * Controller xử lý các API Booking của Warehouse Owner.
 *
 * Endpoints:
 *   GET   /api/owner/bookings              — Xem yêu cầu đến (phân trang)
 *   PATCH /api/owner/bookings/{id}/approve — Chấp nhận yêu cầu
 *   PATCH /api/owner/bookings/{id}/reject  — Từ chối yêu cầu
 */
@Tag(name = "Owner — Booking", description = "API xét duyệt yêu cầu thuê kho của Owner")
@RestController
@RequestMapping("/api/owner/bookings")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final BookingService bookingService;

    /**
     * GET /api/owner/bookings
     * Danh sách yêu cầu thuê đến kho của Owner, phân trang.
     */
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_READ')")
    @Operation(summary = "Xem danh sách yêu cầu thuê kho đến (Owner)")
    public ResponseEntity<ApiResponse<PagedResponse<BookingResponse>>> getIncomingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.util.UUID ownerId = getCurrentUserId();
        PagedResponse<BookingResponse> result = bookingService.getIncomingRequests(ownerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu thuê thành công", result));
    }

    /**
     * PATCH /api/owner/bookings/{id}/approve
     * Chấp nhận yêu cầu thuê kho.
     * Tự động: deduct deposit + tạo hợp đồng + warehouse RENTED.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_PROCESS')")
    @Operation(summary = "Chấp nhận yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<BookingResponse>> approve(@PathVariable java.util.UUID id) {
        java.util.UUID ownerId = getCurrentUserId();
        BookingResponse response = bookingService.approveBooking(ownerId, id);
        return ResponseEntity.ok(ApiResponse.success("Chấp nhận yêu cầu thuê kho thành công", response));
    }

    /**
     * PATCH /api/owner/bookings/{id}/reject
     * Từ chối yêu cầu thuê kho.
     * Body: { "reason": "..." }
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_PROCESS')")
    @Operation(summary = "Từ chối yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<BookingResponse>> reject(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        java.util.UUID ownerId = getCurrentUserId();
        BookingResponse response = bookingService.rejectBooking(ownerId, id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Từ chối yêu cầu thuê kho thành công", response));
    }

    private java.util.UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
