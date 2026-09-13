package fu.stockspace.stockspace_be.wms.dataexchange.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DataExchangeProperties.class)
public class DataExchangeConfig {
}
