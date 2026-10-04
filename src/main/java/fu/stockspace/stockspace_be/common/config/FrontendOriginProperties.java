package fu.stockspace.stockspace_be.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides one source of truth for HTTP CORS and WebSocket handshake origins.
 */
@Component
@ConfigurationProperties(prefix = "app.frontend")
public class FrontendOriginProperties {

    private String url = "http://localhost:5173";
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "https://stock-space-nu.vercel.app"
    ));

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedOrigins);
    }

    public List<String> resolvedAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        allowedOrigins.stream()
                .map(FrontendOriginProperties::normalize)
                .filter(origin -> !origin.isBlank())
                .forEach(origins::add);

        String normalizedUrl = normalize(url);
        if (!normalizedUrl.isBlank()) {
            origins.add(normalizedUrl);
        }

        return List.copyOf(origins);
    }

    private static String normalize(String origin) {
        if (origin == null) {
            return "";
        }
        return origin.trim().replaceAll("/+$", "");
    }
}
