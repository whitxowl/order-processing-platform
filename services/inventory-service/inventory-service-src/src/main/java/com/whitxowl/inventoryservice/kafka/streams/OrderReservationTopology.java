package com.whitxowl.inventoryservice.kafka.streams;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.service.InventoryService;
import com.whitxowl.orderservice.events.order.OrderCreated;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.time.Instant;

@Slf4j
@Configuration
@EnableKafkaStreams
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.streams.application-id")
public class OrderReservationTopology {

    private final InventoryService inventoryService;

    @Value("${app.kafka.topics.order-created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.inventory-reserved}")
    private String inventoryReservedTopic;

    @Bean
    public KStream<String, OrderCreated> orderReservationStream(
            StreamsBuilder builder,
            SpecificAvroSerde<OrderCreated> orderCreatedSerde,
            SpecificAvroSerde<InventoryReserved> inventoryReservedSerde) {

        KStream<String, OrderCreated> stream = builder.stream(
                orderCreatedTopic,
                Consumed.with(Serdes.String(), orderCreatedSerde));

        stream
                .mapValues(this::processReservation)
                .to(inventoryReservedTopic, Produced.with(Serdes.String(), inventoryReservedSerde));

        return stream;
    }

    private InventoryReserved processReservation(OrderCreated event) {
        String orderId   = event.getOrderId();
        String productId = event.getProductId();
        int    quantity  = event.getQuantity();

        log.info("Streams: processing order.created [orderId={}, productId={}, quantity={}]",
                orderId, productId, quantity);

        try {
            inventoryService.reserve(orderId, productId, quantity);

            log.info("Streams: reservation succeeded [orderId={}]", orderId);

            return InventoryReserved.newBuilder()
                    .setOrderId(orderId)
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .setSuccess(true)
                    .setReason("")
                    .setReservedAt(Instant.now())
                    .build();

        } catch (DuplicateReservationException e) {
            log.warn("Streams: duplicate order.created ignored [orderId={}]", orderId);

            return InventoryReserved.newBuilder()
                    .setOrderId(orderId)
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .setSuccess(true)
                    .setReason("duplicate - already reserved")
                    .setReservedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Streams: reservation failed [orderId={}, reason={}]", orderId, e.getMessage());

            return InventoryReserved.newBuilder()
                    .setOrderId(orderId)
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .setSuccess(false)
                    .setReason(e.getMessage())
                    .setReservedAt(Instant.now())
                    .build();
        }
    }
}