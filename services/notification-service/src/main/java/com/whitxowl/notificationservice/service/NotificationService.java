package com.whitxowl.notificationservice.service;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.orderservice.events.order.OrderStatusChanged;
import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.userservice.events.user.UserRoleChanged;

public interface NotificationService {

    void sendUserCreated(UserCreated event);

    void sendRoleChanged(UserRoleChanged event);

    void sendOrderCreated(OrderCreated event);

    void sendOrderStatusChanged(OrderStatusChanged event);
}