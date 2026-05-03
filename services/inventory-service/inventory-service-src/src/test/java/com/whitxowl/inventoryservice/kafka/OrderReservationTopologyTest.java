package com.whitxowl.inventoryservice.kafka;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.exception.InsufficientStockException;
import com.whitxowl.inventoryservice.kafka.streams.OrderReservationTopology;
import com.whitxowl.inventoryservice.service.InventoryService;
import com.whitxowl.orderservice.events.order.OrderCreated;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReservationTopologyTest {

    private static final String MOCK_REGISTRY_SCOPE = "test-scope";
    private static final String MOCK_REGISTRY_URL   = "mock://" + MOCK_REGISTRY_SCOPE;

    private static final String ORDER_CREATED_TOPIC      = "order.created";
    private static final String INVENTORY_RESERVED_TOPIC = "inventory.reserved";

    @Mock
    private InventoryService inventoryService;

    private TopologyTestDriver driver;
    private TestInputTopic<String, OrderCreated> inputTopic;
    private TestOutputTopic<String, InventoryReserved> outputTopic;

    @BeforeEach
    void setUp() {
        Map<String, String> serdeConfig = Map.of(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_REGISTRY_URL
        );

        SpecificAvroSerde<OrderCreated> orderSerde = new SpecificAvroSerde<>();
        orderSerde.configure(serdeConfig, false);

        SpecificAvroSerde<InventoryReserved> reservedSerde = new SpecificAvroSerde<>();
        reservedSerde.configure(serdeConfig, false);

        OrderReservationTopology topology = new OrderReservationTopology(inventoryService);

        setField(topology, "orderCreatedTopic",      ORDER_CREATED_TOPIC);
        setField(topology, "inventoryReservedTopic", INVENTORY_RESERVED_TOPIC);

        StreamsBuilder builder = new StreamsBuilder();
        topology.orderReservationStream(builder, orderSerde, reservedSerde);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG,    "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.String().getClass().getName());

        driver = new TopologyTestDriver(builder.build(), props);

        inputTopic = driver.createInputTopic(
                ORDER_CREATED_TOPIC,
                Serdes.String().serializer(),
                orderSerde.serializer());

        outputTopic = driver.createOutputTopic(
                INVENTORY_RESERVED_TOPIC,
                Serdes.String().deserializer(),
                reservedSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
        MockSchemaRegistry.dropScope(MOCK_REGISTRY_SCOPE);
    }

    @Test
    void whenReservationSucceeds_shouldPublishSuccessEvent() {
        String orderId   = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        inputTopic.pipeInput(orderId, orderCreated(orderId, productId, 3));

        verify(inventoryService).reserve(orderId, productId, 3);

        InventoryReserved result = outputTopic.readValue();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getReason()).isEqualTo("");
    }

    @Test
    void whenInsufficientStock_shouldPublishFailureEvent() {
        String orderId   = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        doThrow(new InsufficientStockException(productId, 5, 2))
                .when(inventoryService).reserve(orderId, productId, 5);

        inputTopic.pipeInput(orderId, orderCreated(orderId, productId, 5));

        InventoryReserved result = outputTopic.readValue();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getReason()).isNotBlank();
    }

    @Test
    void whenDuplicateReservation_shouldPublishSuccessEventAndNotFail() {
        String orderId   = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        doThrow(new DuplicateReservationException(orderId))
                .when(inventoryService).reserve(orderId, productId, 1);

        inputTopic.pipeInput(orderId, orderCreated(orderId, productId, 1));

        assertThat(outputTopic.isEmpty()).isFalse();
        InventoryReserved result = outputTopic.readValue();

        assertThat(result.getSuccess()).isTrue();
    }

    @Test
    void outputMessageKey_shouldMatchOrderId() {
        String orderId   = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        inputTopic.pipeInput(orderId, orderCreated(orderId, productId, 2));

        KeyValue<String, InventoryReserved> record = outputTopic.readKeyValue();
        assertThat(record.key).isEqualTo(orderId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OrderCreated orderCreated(String orderId, String productId, int quantity) {
        return OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("user-1")
                .setProductId(productId)
                .setQuantity(quantity)
                .setCreatedAt(Instant.now())
                .build();
    }

    private void setField(Object target, String fieldName, String value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}