package fu.stockspace.stockspace_be.wms.dataexchange.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "wms.data-exchange")
public class DataExchangeProperties {

    private long maxFileBytes = 10L * 1024L * 1024L;
    private int maxRows = 10_000;
    private int maxMovementGroups = 1_000;
    private int maxErrorsInline = 200;
    private int maxTextCellLength = 2_000;
    private int maxJsonCellLength = 20_000;
}
