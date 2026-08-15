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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;











@Tag(name = "Tenant — Booking", description = "API gửi và quản lý yêu cầu thuê kho của Tenant")
@RestController
@RequestMapping("/api/tenant/bookings")
@RequiredArgsConstructor
public class TenantBookingController {

    private final BookingService bookingService;





    @PostMapping
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_CREATE')")
    @Operation(summary = "Gửi yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<BookingResponse>> sendRequest(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        java.util.UUID tenantId = getCurrentUserId();
        BookingResponse response = bookingService.sendBookingRequest(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi yêu cầu thuê kho thành công. Đang chờ Owner xét duyệt.", response));
    }





    @GetMapping
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_READ')")
    @Operation(summary = "Xem lịch sử yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<PagedResponse<BookingResponse>>> getMyBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.util.UUID tenantId = getCurrentUserId();
        PagedResponse<BookingResponse> result = bookingService.getMyBookings(tenantId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách booking thành công", result));
    }





    @DeleteMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('RENTAL_REQUEST_CREATE')")
    @Operation(summary = "Huỷ yêu cầu thuê kho")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable java.util.UUID id) {
        java.util.UUID tenantId = getCurrentUserId();
        bookingService.cancelBooking(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Huỷ yêu cầu thuê kho thành công", null));
    }

    private java.util.UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
