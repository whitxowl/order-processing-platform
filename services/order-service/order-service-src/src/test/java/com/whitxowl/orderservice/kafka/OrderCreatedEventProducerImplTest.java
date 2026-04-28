package com.whitxowl.orderservice.kafka;

import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.orderservice.kafka.producer.impl.OrderCreatedEventProducerImpl;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OrderCreatedEventProducerImplTest {

    private static final String TOPIC     = "order.created";
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

    static final Network network = Network.newNetwork();

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

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaConsumer<String, Object> consumer;
    private OrderCreatedEventProducerImpl producer;

    @BeforeEach
    void setUp() {
        String bootstrapServers  = kafka.getBootstrapServers();
        String schemaRegistryUrl = "http://" + schemaRegistry.getHost()
                + ":" + schemaRegistry.getMappedPort(8081);

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl,
                ProducerConfig.ACKS_CONFIG, "all"
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,                 "test-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.assign(Collections.singletonList(PARTITION));

        producer = new OrderCreatedEventProducerImpl(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "orderCreatedTopic", TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void produce_shouldDeliverEvent_withCorrectContent() {
        long offsetBefore = endOffset();
        String orderId = UUID.randomUUID().toString();

        producer.produce(orderId, "user-1", "product-1", 3);
        kafkaTemplate.flush();

        OrderCreated received = pollFrom(offsetBefore);

        assertThat(received.getOrderId()).isEqualTo(orderId);
        assertThat(received.getUserId()).isEqualTo("user-1");
        assertThat(received.getProductId()).isEqualTo("product-1");
        assertThat(received.getQuantity()).isEqualTo(3);
        assertThat(received.getCreatedAt()).isNotNull();
    }

    @Test
    void produce_shouldUseOrderIdAsMessageKey() {
        long offsetBefore = endOffset();
        String orderId = UUID.randomUUID().toString();

        producer.produce(orderId, "user-1", "product-1", 1);
        kafkaTemplate.flush();

        ConsumerRecord<String, Object> record = pollRecordFrom(offsetBefore);

        assertThat(record.key()).isEqualTo(orderId);
    }

    private long endOffset() {
        return consumer.endOffsets(Collections.singletonList(PARTITION))
                .getOrDefault(PARTITION, 0L);
    }

    private OrderCreated pollFrom(long fromOffset) {
        return (OrderCreated) pollRecordFrom(fromOffset).value();
    }

    private ConsumerRecord<String, Object> pollRecordFrom(long fromOffset) {
        consumer.seek(PARTITION, fromOffset);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, Object> record : records) {
                return record;
            }
        }
        throw new AssertionError("No message received from Kafka within timeout");
    }
}