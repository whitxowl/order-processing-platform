package com.whitxowl.orderservice.kafka.consumer;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import org.springframework.kafka.support.Acknowledgment;

public interface InventoryReservedEventListener {

    void onInventoryReserved(InventoryReserved event, Acknowledgment acknowledgment);
}