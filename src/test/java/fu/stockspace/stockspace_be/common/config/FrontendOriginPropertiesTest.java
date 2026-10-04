package fu.stockspace.stockspace_be.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrontendOriginPropertiesTest {

    @Test
    void resolvedAllowedOriginsNormalizesAndDeduplicatesConfiguration() {
        FrontendOriginProperties properties = new FrontendOriginProperties();
        properties.setUrl(" https://stock-space-nu.vercel.app/ ");
        properties.setAllowedOrigins(List.of(
                "http://localhost:5173/",
                "https://stock-space-nu.vercel.app",
                "  "
        ));

        assertEquals(
                List.of(
                        "http://localhost:5173",
                        "https://stock-space-nu.vercel.app"
                ),
                properties.resolvedAllowedOrigins()
        );
    }

    @Test
    void corsOnlyAllowsExplicitlyConfiguredVercelOrigins() {
        FrontendOriginProperties properties = new FrontendOriginProperties();
        properties.setUrl("");
        properties.setAllowedOrigins(List.of(
                "https://stock-space-nu.vercel.app",
                "https://stock-space-preview-team.vercel.app"
        ));

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.resolvedAllowedOrigins());

        String previewOrigin = "https://stock-space-preview-team.vercel.app";
        assertEquals(previewOrigin, cors.checkOrigin(previewOrigin));
        assertNull(cors.checkOrigin("https://stock-space-attacker.vercel.app"));
    }
}
