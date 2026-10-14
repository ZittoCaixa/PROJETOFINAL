package com.flashsale.checkout.service;

import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class OrderPublisher {

    private static final String ORDER_CREATED_QUEUE = "order-created";

    public void publish(OrderCreatedEvent event) {
        EmbeddedRabbitBroker.getInstance().publish(ORDER_CREATED_QUEUE, event);
    }
}

