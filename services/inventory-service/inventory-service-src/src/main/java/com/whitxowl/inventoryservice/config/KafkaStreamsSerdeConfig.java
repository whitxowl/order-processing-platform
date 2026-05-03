package com.whitxowl.inventoryservice.config;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.orderservice.events.order.OrderCreated;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.streams.application-id")
public class KafkaStreamsSerdeConfig {

    @Value("${spring.kafka.streams.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Bean
    public SpecificAvroSerde<OrderCreated> orderCreatedSerde() {
        SpecificAvroSerde<OrderCreated> serde = new SpecificAvroSerde<>();
        serde.configure(serdeConfig(), false);
        return serde;
    }

    @Bean
    public SpecificAvroSerde<InventoryReserved> inventoryReservedSerde() {
        SpecificAvroSerde<InventoryReserved> serde = new SpecificAvroSerde<>();
        serde.configure(serdeConfig(), false);
        return serde;
    }

    private Map<String, String> serdeConfig() {
        return Map.of(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl
        );
    }
}