package com.dha.queue.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A bare `new RestTemplate()` has no timeout at all — a hung branch-service
 * call would block the calling request thread indefinitely.
 *
 * The explicit JdkClientHttpRequestFactory pinned to HTTP_1_1 matters: with
 * no Apache HttpClient/Jetty/OkHttp on the classpath, RestTemplateBuilder's
 * auto-detection falls back to java.net.http.HttpClient, whose default
 * preferred version is HTTP_2 — for a plaintext http:// URL that means it
 * first tries an HTTP/1.1 "h2c" cleartext upgrade handshake. branch-service
 * (Tomcat) tolerates that fine today, but a uvicorn-backed Python service
 * doesn't (found the hard way debugging face-service/ml-service calls) —
 * pinned here too so this copy doesn't reintroduce the same bug the moment
 * this client is ever pointed at one.
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
