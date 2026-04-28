package com.whitxowl.notificationservice.service;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.userservice.events.user.UserRoleChanged;

public interface NotificationService {

    void sendUserCreated(UserCreated event);

    void sendRoleChanged(UserRoleChanged event);

    void sendOrderCreated(OrderCreated event);

    void sendInventoryReserved(InventoryReserved event);
}