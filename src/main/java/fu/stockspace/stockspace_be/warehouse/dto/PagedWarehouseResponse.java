package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Wrapper phân trang cho danh sách Warehouse.
 * Giữ cùng cấu trúc với PagedUserResponse (totalElements, totalPages, v.v.)
 */
@Getter
@Builder
public class PagedWarehouseResponse {

    private List<WarehouseResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
