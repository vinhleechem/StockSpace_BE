package fu.stockspace.stockspace_be.listing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides the business clock for scheduled listing publication.
 *
 * <p>The platform currently operates in Ho Chi Minh City. Keeping the zone
 * explicit prevents publication dates from depending on the host machine's
 * default timezone.</p>
 */
@Configuration
public class ListingPublicationTimeConfig {

    public static final String PUBLICATION_CLOCK_BEAN = "listingPublicationClock";
    public static final ZoneId PUBLICATION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean(name = PUBLICATION_CLOCK_BEAN)
    public Clock listingPublicationClock() {
        return Clock.system(PUBLICATION_ZONE);
    }
}
