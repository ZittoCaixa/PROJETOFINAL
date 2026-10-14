package com.flashsale.checkout.service;

import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.infra.InMemoryRedisStore;
import com.flashsale.checkout.infra.OrderTrackingStore;
import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class CompensationConsumer {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

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
            handle(event);
        });
    }

    void handle(OrderCompensationEvent event) {
        redisStore.releaseStock(event.produtoId(), event.quantidade());
        trackingStore.findById(event.pedidoId()).ifPresent(existing -> {
            OrderSnapshot cancelled = new OrderSnapshot(
                    existing.pedidoId(),
                    existing.produtoId(),
                    existing.quantidade(),
                    existing.clienteCpf(),
                    existing.clienteNome(),
                    OrderStatus.CANCELLED,
                    LocalDateTime.now()
            );
            trackingStore.upsert(cancelled);
            redisStore.putOrder("order:" + cancelled.pedidoId(), cancelled, CACHE_TTL);
        });
    }
}
