package fu.stockspace.stockspace_be.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/** Thông tin user trả về từ Google API (GET /oauth2/v1/userinfo) */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserInfo {

    private String id;

    private String email;

    @JsonProperty("verified_email")
    private Boolean verifiedEmail;

    private String name;

    @JsonProperty("given_name")
    private String givenName;

    @JsonProperty("family_name")
    private String familyName;

    private String picture;

    private String locale;
}
