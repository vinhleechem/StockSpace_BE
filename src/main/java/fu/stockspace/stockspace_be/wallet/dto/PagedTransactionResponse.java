package fu.stockspace.stockspace_be.wallet.dto;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
/**
 * Wrapper phân trang cho TransactionResponse.
 */
@Getter
@Builder
public class PagedTransactionResponse {
    private List<TransactionResponse> content;
    private int page;
    private int size;
    private int pageNo;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public int getPageNo() {
        return pageNo != 0 ? pageNo : page;
    }

    public int getPageSize() {
        return pageSize != 0 ? pageSize : size;
    }
}