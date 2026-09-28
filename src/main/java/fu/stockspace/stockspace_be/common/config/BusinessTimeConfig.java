package fu.stockspace.stockspace_be.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BusinessTimeConfig {

    public static final String BUSINESS_CLOCK_BEAN = "businessClock";
    public static final String BUSINESS_ZONE = "Asia/Ho_Chi_Minh";
    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of(BUSINESS_ZONE);

    @Bean(name = BUSINESS_CLOCK_BEAN)
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE_ID);
    }
}
