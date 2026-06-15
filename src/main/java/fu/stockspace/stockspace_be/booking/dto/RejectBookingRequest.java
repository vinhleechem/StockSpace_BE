package fu.stockspace.stockspace_be.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO khi Owner từ chối một BookingRequest.
 */
@Getter
@Setter
public class RejectBookingRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}
