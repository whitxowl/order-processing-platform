package com.whitxowl.authservice.kafka;

import com.whitxowl.authservice.domain.entity.RoleEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.authservice.kafka.producer.impl.UserCreatedEventProducerImpl;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UserCreatedEventProducerImplTest {

    private static final String TOPIC = "user.created";
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
    private UserCreatedEventProducerImpl producer;

    @BeforeEach
    void setUp() {
        String bootstrapServers = kafka.getBootstrapServers();
        String schemaRegistryUrl = "http://" + schemaRegistry.getHost()
                + ":" + schemaRegistry.getMappedPort(8081);

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl,
                ProducerConfig.ACKS_CONFIG, "all"
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.assign(Collections.singletonList(PARTITION));

        producer = new UserCreatedEventProducerImpl(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "userCreatedTopic", TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void produce_shouldDeliverMessageToKafka_withCorrectContent() {
        UserEntity user = buildUser("ROLE_USER");
        long offsetBefore = endOffset();

        producer.produce(user, "test-verification-token-hex");
        kafkaTemplate.flush();

        UserCreated received = pollFrom(offsetBefore);

        assertThat(received.getUserId()).isEqualTo(user.getId());
        assertThat(received.getEmail()).isEqualTo(user.getEmail());
        assertThat(received.getRoles()).containsExactly("ROLE_USER");
        assertThat(received.getVerificationToken()).isEqualTo("test-verification-token-hex");
        assertThat(received.getCreatedAt()).isNotNull();
    }

    @Test
    void produce_shouldUseUserIdAsMessageKey() {
        UserEntity user = buildUser("ROLE_USER");
        long offsetBefore = endOffset();

        producer.produce(user, "test-verification-token-hex");
        kafkaTemplate.flush();

        ConsumerRecord<String, Object> record = pollRecordFrom(offsetBefore);

        assertThat(record.key()).isEqualTo(user.getId().toString());
    }

    @Test
    void produce_shouldDeliverMultipleRoles() {
        UserEntity user = buildUser("ROLE_USER", "ROLE_MANAGER");
        long offsetBefore = endOffset();

        producer.produce(user, "test-verification-token-hex");
        kafkaTemplate.flush();

        UserCreated received = pollFrom(offsetBefore);

        assertThat(received.getRoles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_MANAGER");
    }

    private long endOffset() {
        return consumer.endOffsets(Collections.singletonList(PARTITION))
                .getOrDefault(PARTITION, 0L);
    }

    private UserCreated pollFrom(long fromOffset) {
        return (UserCreated) pollRecordFrom(fromOffset).value();
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

    private UserEntity buildUser(String... roleNames) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        Set<RoleEntity> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            RoleEntity role = new RoleEntity();
            role.setRole(roleName);
            roles.add(role);
        }
        user.setRoles(roles);
        return user;
    }
}