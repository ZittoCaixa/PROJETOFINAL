package com.flashsale.checkout.service;

import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.infra.InMemoryRedisStore;
import com.flashsale.checkout.infra.OrderTrackingStore;
import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CompensationConsumer {

    private final InMemoryRedisStore redisStore;
    private final OrderTrackingStore trackingStore;

    public CompensationConsumer(InMemoryRedisStore redisStore, OrderTrackingStore trackingStore) {
        this.redisStore = redisStore;
        this.trackingStore = trackingStore;
    }

    @PostConstruct
    void subscribe() {
        EmbeddedRabbitBroker.getInstance().subscribe("order-compensation", message -> {
            if (!(message instanceof OrderCompensationEvent event)) {
                return;
            }
            redisStore.releaseStock(event.produtoId(), event.quantidade());
            trackingStore.findById(event.pedidoId()).ifPresent(existing -> trackingStore.upsert(
                    new OrderSnapshot(
                            existing.pedidoId(),
                            existing.produtoId(),
                            existing.quantidade(),
                            existing.clienteCpf(),
                            existing.clienteNome(),
                            OrderStatus.CANCELLED,
                            LocalDateTime.now()
                    )
            ));
        });
    }
}

