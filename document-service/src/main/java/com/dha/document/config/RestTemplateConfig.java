package com.dha.document.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A bare `new RestTemplate()` has no timeout at all — a hung ml-service call
 * would block the upload request thread indefinitely instead of falling
 * through to MlServiceClient's fail-open catch block.
 *
 * The explicit JdkClientHttpRequestFactory pinned to HTTP_1_1 matters: with
 * no Apache HttpClient/Jetty/OkHttp on the classpath, RestTemplateBuilder's
 * auto-detection falls back to java.net.http.HttpClient, whose default
 * preferred version is HTTP_2 — for a plaintext http:// URL that means it
 * first tries an HTTP/1.1 "h2c" cleartext upgrade handshake. ml-service's
 * uvicorn/h11 server doesn't tolerate that Upgrade header on a plain
 * endpoint and responds with a bare 400 instead of just ignoring it and
 * proceeding on HTTP/1.1 — which silently sent every quality/classification
 * call straight into MlServiceClient's fail-open catch block until pinned
 * here (found while debugging the same issue against face-service).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return builder
                .requestFactory(() -> new JdkClientHttpRequestFactory(httpClient))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
