package fu.stockspace.stockspace_be.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/** Response từ Google OAuth token endpoint */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("scope")
    private String scope;
}
