package az.aqroinvest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** One shared outbound client (Esri, Open-Meteo, Anthropic). Per-request timeouts are set by each caller. */
@Configuration
public class HttpConfig {
    @Bean
    HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }
}
