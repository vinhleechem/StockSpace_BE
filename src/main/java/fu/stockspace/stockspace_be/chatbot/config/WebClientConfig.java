package fu.stockspace.stockspace_be.chatbot.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Config WebClient để gọi OpenRouter AI API.
 */
@Configuration
public class WebClientConfig {

    @Value("${app.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    @Value("${app.openrouter.connect-timeout:5s}")
    private Duration connectTimeout;

    @Value("${app.openrouter.response-timeout:30s}")
    private Duration responseTimeout;

    @Value("${app.openrouter.max-in-memory-response:2MB}")
    private org.springframework.util.unit.DataSize maxInMemoryResponse;

    @Bean
    public WebClient webClient() {
        int connectTimeoutMillis = Math.toIntExact(
                Math.max(1, connectTimeout.toMillis())
        );
        return WebClient.builder()
                .baseUrl(openRouterBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(responseTimeout)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                ))
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(maxInMemoryResponse.toBytes())))
                .build();
    }
}
