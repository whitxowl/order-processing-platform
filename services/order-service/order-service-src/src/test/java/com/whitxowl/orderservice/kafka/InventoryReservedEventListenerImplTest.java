package com.whitxowl.orderservice.kafka;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.orderservice.service.OrderService;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class InventoryReservedEventListenerImplTest {

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
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.autoconfigure.exclude", () -> "");

        registry.add("app.kafka.topics.order-created",       () -> "order.created");
        registry.add("app.kafka.topics.inventory-reserved",  () -> "inventory.reserved");
        registry.add("app.kafka.topics.order-status-changed", () -> "order.status-changed");

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
        registry.add("spring.kafka.consumer.group-id",           () -> "order-service-test");
        registry.add("spring.kafka.consumer.auto-offset-reset",  () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> "false");
        registry.add("spring.kafka.consumer.key-deserializer",
                () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer",
                () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        registry.add("spring.kafka.consumer.properties.schema.registry.url",
                () -> "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081));
        registry.add("spring.kafka.consumer.properties.specific.avro.reader", () -> "true");

        registry.add("grpc.client.inventory-service.address",          () -> "static://localhost:9999");
        registry.add("grpc.client.inventory-service.negotiation-type", () -> "plaintext");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private OrderService orderService;

    @Test
    void onInventoryReserved_shouldCallHandleReserved_whenSuccess() {
        String orderId = UUID.randomUUID().toString();

        InventoryReserved event = InventoryReserved.newBuilder()
                .setOrderId(orderId)
                .setProductId("product-1")
                .setQuantity(2)
                .setSuccess(true)
                .setReason(null)
                .setReservedAt(Instant.now())
                .build();

        kafkaTemplate.send("inventory.reserved", orderId, event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(orderService).handleInventoryReserved(orderId, true, null));
    }

    @Test
    void onInventoryReserved_shouldCallHandleReserved_whenFailure() {
        String orderId = UUID.randomUUID().toString();

        InventoryReserved event = InventoryReserved.newBuilder()
                .setOrderId(orderId)
                .setProductId("product-1")
                .setQuantity(2)
                .setSuccess(false)
                .setReason("Insufficient stock")
                .setReservedAt(Instant.now())
                .build();

        kafkaTemplate.send("inventory.reserved", orderId, event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(orderService).handleInventoryReserved(orderId, false, "Insufficient stock"));
    }

    @Test
    void onInventoryReserved_shouldNotAcknowledge_whenServiceThrows() {
        String orderId = UUID.randomUUID().toString();

        doThrow(new RuntimeException("DB down"))
                .when(orderService).handleInventoryReserved(orderId, true, null);

        InventoryReserved event = InventoryReserved.newBuilder()
                .setOrderId(orderId)
                .setProductId("product-1")
                .setQuantity(1)
                .setSuccess(true)
                .setReason(null)
                .setReservedAt(Instant.now())
                .build();

        kafkaTemplate.send("inventory.reserved", orderId, event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(orderService, atLeastOnce()).handleInventoryReserved(orderId, true, null));
    }
}