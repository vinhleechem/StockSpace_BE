package fu.stockspace.stockspace_be.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Request gửi đến Google OAuth token endpoint để exchange authorization code.
 * Feign @QueryMap sẽ serialize fields thành form-encoded parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeTokenRequest {

    private String code;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("redirect_uri")
    private String redirectUri;

    @JsonProperty("grant_type")
    private String grantType;
}
