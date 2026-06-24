package fu.stockspace.stockspace_be.auth.httpclient;

import fu.stockspace.stockspace_be.auth.dto.ExchangeTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client gọi Google OAuth2 để exchange authorization code lấy access token.
 */
@FeignClient(name = "outbound-auth-client", url = "https://oauth2.googleapis.com")
public interface OutboundAuthClient {

    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ExchangeTokenResponse exchangeToken(@RequestBody MultiValueMap<String, String> request);
}
