package com.whitxowl.gateway.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Value("${rate-limiter.burst-capacity}")
    private int burstCapacity;

    @Test
    void whenRequestsExceedBurstCapacity_shouldReturn429() {
        List<HttpStatusCode> statuses = new ArrayList<>();

        for (int i = 0; i <= burstCapacity; i++) {
            HttpStatusCode status = webTestClient.get()
                    .uri("/api/v1/products")
                    .exchange()
                    .returnResult(String.class)
                    .getStatus();
            statuses.add(status);
        }

        assertThat(statuses).contains(HttpStatusCode.valueOf(429));
    }

    @Test
    void differentKeys_shouldHaveSeparateCounters() {
        for (int i = 0; i <= burstCapacity; i++) {
            webTestClient.get().uri("/api/v1/products").exchange();
        }

        HttpStatusCode status = webTestClient.get()
                .uri("/api/v1/products")
                .header("X-User-Id", "test-user-separate")
                .exchange()
                .returnResult(String.class)
                .getStatus();

        assertThat(status).isNotEqualTo(HttpStatusCode.valueOf(429));
    }
}