package fu.stockspace.stockspace_be.auth.httpclient;

import fu.stockspace.stockspace_be.auth.dto.GoogleUserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client gọi Google API để lấy thông tin user bằng access token.
 */
@FeignClient(name = "outbound-user-client", url = "https://www.googleapis.com")
public interface OutboundUserClient {

    @GetMapping("/oauth2/v1/userinfo")
    GoogleUserInfo getUserInfo(
            @RequestParam("alt") String alt,
            @RequestParam("access_token") String accessToken
    );
}
