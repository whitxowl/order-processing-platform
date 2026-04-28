package com.whitxowl.userservice.kafka;

import com.whitxowl.userservice.domain.entity.RoleEntity;
import com.whitxowl.userservice.domain.entity.UserEntity;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import com.whitxowl.userservice.kafka.producer.impl.UserRoleChangedEventProducerImpl;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UserRoleChangedEventProducerImplTest {

    private static final String TOPIC = "user.role-changed";
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
    private UserRoleChangedEventProducerImpl producer;
    private String schemaRegistryUrl;

    @BeforeEach
    void setUp() {
        String bootstrapServers = kafka.getBootstrapServers();
        schemaRegistryUrl = "http://" + schemaRegistry.getHost()
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

        producer = new UserRoleChangedEventProducerImpl(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "userRoleChangedTopic", TOPIC);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void produce_shouldDeliverMessageToKafka_withCorrectContent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "ROLE_USER", "ROLE_MANAGER");

        long offsetBefore = endOffset();

        producer.produce(user);
        kafkaTemplate.flush();

        UserRoleChanged received = pollFrom(offsetBefore);

        assertThat(received.getUserId()).isEqualTo(userId.toString());
        assertThat(received.getRoles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_MANAGER");
        assertThat(received.getChangedAt()).isNotNull();
    }

    @Test
    void produce_shouldUseUserIdAsMessageKey() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "ROLE_ADMIN");

        long offsetBefore = endOffset();

        producer.produce(user);
        kafkaTemplate.flush();

        ConsumerRecord<String, Object> record = pollRecordFrom(offsetBefore);

        assertThat(record.key()).isEqualTo(userId.toString());
    }

    @Test
    void produce_shouldDeliverEmptyRoles_whenUserHasNoRoles() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);

        long offsetBefore = endOffset();

        producer.produce(user);
        kafkaTemplate.flush();

        UserRoleChanged received = pollFrom(offsetBefore);

        assertThat(received.getUserId()).isEqualTo(userId.toString());
        assertThat(received.getRoles()).isEmpty();
    }

    @Test
    void produce_twiceForSameUser_shouldDeliverTwoMessagesInOrder() {
        UUID userId = UUID.randomUUID();
        UserEntity userBefore = buildUser(userId, "ROLE_USER");
        UserEntity userAfter  = buildUser(userId, "ROLE_USER", "ROLE_MANAGER");

        long offsetBefore = endOffset();

        producer.produce(userBefore);
        producer.produce(userAfter);
        kafkaTemplate.flush();

        List<UserRoleChanged> events = pollAllFrom(offsetBefore, 2);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getRoles()).containsExactly("ROLE_USER");
        assertThat(events.get(1).getRoles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_MANAGER");
    }

    private long endOffset() {
        var endOffsets = consumer.endOffsets(Collections.singletonList(PARTITION));
        return endOffsets.getOrDefault(PARTITION, 0L);
    }

    private UserRoleChanged pollFrom(long fromOffset) {
        return (UserRoleChanged) pollRecordFrom(fromOffset).value();
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

    private List<UserRoleChanged> pollAllFrom(long fromOffset, int expectedCount) {
        consumer.seek(PARTITION, fromOffset);
        List<UserRoleChanged> result = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (result.size() < expectedCount && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, Object> record : records) {
                result.add((UserRoleChanged) record.value());
            }
        }
        return result;
    }

    private UserEntity buildUser(UUID id, String... roleNames) {
        Set<RoleEntity> roles = new HashSet<>();
        for (String roleName : roleNames) {
            RoleEntity role = new RoleEntity();
            role.setRole(roleName);
            roles.add(role);
        }
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("test@example.com");
        user.setRoles(roles);
        return user;
    }
}
