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
    private long totalElements;
    private int totalPages;
    private boolean last;
}