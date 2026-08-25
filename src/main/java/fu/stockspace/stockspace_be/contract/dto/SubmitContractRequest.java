package fu.stockspace.stockspace_be.contract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class SubmitContractRequest {

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDate endDate;

    @NotEmpty(message = "Tệp hợp đồng giấy không được để trống")
    private List<String> paperContractFiles;

    /** @deprecated Use paperContractFiles. */
    @Deprecated
    @JsonProperty("paperContractImages")
    public List<String> getPaperContractImages() {
        return paperContractFiles;
    }

    /** @deprecated Use paperContractFiles. */
    @Deprecated
    @JsonProperty("paperContractImages")
    public void setPaperContractImages(List<String> paperContractImages) {
        this.paperContractFiles = paperContractImages;
    }
}
