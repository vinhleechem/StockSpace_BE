package fu.stockspace.stockspace_be.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Wrapper phân trang cho BookingResponse.
 */
@Getter
@Builder
public class PagedBookingResponse {
    private List<BookingResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
