package com.flashsale.checkout.infra;

import com.flashsale.checkout.domain.OrderSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

@Component
public class OrderTrackingStore {

    private final Map<UUID, OrderSnapshot> orders = new ConcurrentHashMap<>();

    public void upsert(OrderSnapshot snapshot) {
        orders.put(snapshot.pedidoId(), snapshot);
    }

    public Optional<OrderSnapshot> findById(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<OrderSnapshot> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(OrderSnapshot::dataHoraAtualizacao).reversed())
                .toList();
    }
}
