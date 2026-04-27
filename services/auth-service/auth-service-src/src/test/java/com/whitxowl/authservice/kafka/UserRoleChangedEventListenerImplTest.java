package com.whitxowl.authservice.kafka;

import com.whitxowl.authservice.service.AuthService;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class UserRoleChangedEventListenerImplTest {

    static final Network network = Network.newNetwork();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withNetwork(network)
            .withNetworkAliases("kafka");

    @Container
    static final GenericContainer<?> schemaRegistry = new GenericContainer<>(
            DockerImageName.parse("confluentinc/cp-schema-registry:7.6.0"))
            .withNetwork(network)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "kafka:9092")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
            .dependsOn(kafka);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.autoconfigure.exclude", () -> "");

        registry.add("app.kafka.topics.user-created", () -> "user.created");
        registry.add("app.kafka.topics.user-role-changed", () -> "user.role-changed");  // ← это было пропущено

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.listener.ack-mode", () -> "manual");
        registry.add("spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
                () -> "io.confluent.kafka.serializers.KafkaAvroSerializer");
        registry.add("spring.kafka.producer.acks", () -> "all");
        registry.add("spring.kafka.producer.properties.schema.registry.url",
                () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.producer.properties.auto.register.schemas", () -> "true");
        registry.add("spring.kafka.consumer.group-id", () -> "auth-service-test");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> "false");
        registry.add("spring.kafka.consumer.key-deserializer",
                () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer",
                () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        registry.add("spring.kafka.consumer.properties.schema.registry.url",
                () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.consumer.properties.specific.avro.reader", () -> "true");

        registry.add("security.jwt.secret",
                () -> "dGhpcyBpcyBhIHZlcnkgc2VjcmV0IGtleSBmb3IgZGV2ZWxvcG1lbnQgb25seSEhIQ==");
        registry.add("security.jwt.access-ttl", () -> "15m");
        registry.add("security.jwt.refresh-ttl", () -> "7d");
        registry.add("security.jwt.issuer", () -> "auth-service");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private AuthService authService;

    @Test
    void onUserRoleChanged_shouldCallSyncRoles_whenMessageReceived() {
        UUID userId = UUID.randomUUID();
        List<String> roles = List.of("ROLE_USER", "ROLE_MANAGER");

        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(userId.toString())
                .setRoles(roles)
                .setChangedAt(Instant.now())
                .build();

        kafkaTemplate.send("user.role-changed", userId.toString(), event);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(authService).syncRoles(userId.toString(), roles));
    }

    @Test
    void onUserRoleChanged_shouldNotAcknowledge_whenServiceThrows() {
        UUID userId = UUID.randomUUID();
        List<String> roles = List.of("ROLE_USER");

        doThrow(new RuntimeException("DB down"))
                .when(authService).syncRoles(userId.toString(), roles);

        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(userId.toString())
                .setRoles(roles)
                .setChangedAt(Instant.now())
                .build();

        kafkaTemplate.send("user.role-changed", userId.toString(), event);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(authService, atLeastOnce()).syncRoles(userId.toString(), roles));
    }
}