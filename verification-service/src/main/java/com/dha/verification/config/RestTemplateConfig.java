package com.dha.verification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A bare `new RestTemplate()` has no timeout at all — a hung face-service
 * call would block the calling thread indefinitely. 5s connect / 60s read —
 * DeepFace/Facenet512 embedding extraction is CPU-bound and genuinely takes
 * 20-30s+ on this hardware; a shorter read timeout (previously 10s, matching
 * the Gateway's own now-bumped response-timeout) was aborting the client
 * side and reporting "face service unavailable" on calls that were actually
 * about to succeed — confirmed by face-service's own logs completing a
 * PASS result ~20s after verification-service had already given up on it.
 *
 * The explicit JdkClientHttpRequestFactory pinned to HTTP_1_1 matters: with
 * no Apache HttpClient/Jetty/OkHttp on the classpath, RestTemplateBuilder's
 * auto-detection falls back to java.net.http.HttpClient, whose default
 * preferred version is HTTP_2 — for a plaintext http:// URL that means it
 * first tries an HTTP/1.1 "h2c" cleartext upgrade handshake. uvicorn's h11
 * server doesn't tolerate that Upgrade header on a plain endpoint and
 * responds with a bare 400 ("Unsupported upgrade request." / "Invalid HTTP
 * request received." in its own logs) instead of just ignoring it and
 * proceeding on HTTP/1.1 — which silently broke every call to face-service's
 * /check-liveness (and would break any future large-request Python service
 * call from this client) until pinned here.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Connect timeout has to be set on the HttpClient itself — JDK's
        // HttpClient applies it at the connection level, not per-request, so
        // RestTemplateBuilder.connectTimeout() has no effect on this factory.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return builder
                .requestFactory(() -> new JdkClientHttpRequestFactory(httpClient))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }
}
