package com.flashsale.checkout.service;

import com.flashsale.checkout.api.CreateOrderRequest;
import com.flashsale.checkout.domain.OrderProcessingTicket;
import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.infra.InMemoryRedisStore;
import com.flashsale.checkout.infra.OrderQueryGateway;
import com.flashsale.checkout.infra.OrderTrackingStore;
import com.flashsale.shared.OrderCreatedEvent;
import com.flashsale.shared.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int FAILURE_STATUS_ATTEMPTS = 10;

    private final InMemoryRedisStore redisStore;
    private final OrderPublisher orderPublisher;
    private final OrderQueryGateway orderQueryGateway;
    private final OrderTrackingStore trackingStore;

    public OrderService(
            InMemoryRedisStore redisStore,
            OrderPublisher orderPublisher,
            OrderQueryGateway orderQueryGateway,
            OrderTrackingStore trackingStore
    ) {
        this.redisStore = redisStore;
        this.orderPublisher = orderPublisher;
        this.orderQueryGateway = orderQueryGateway;
        this.trackingStore = trackingStore;
    }

    public OrderProcessingTicket createOrder(CreateOrderRequest request) {
        boolean reserved = redisStore.reserveStock(request.produtoId(), request.quantidade());
        if (!reserved) {
            throw new BusinessException("Estoque insuficiente para o produto " + request.produtoId());
        }

        UUID pedidoId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        trackingStore.upsert(new OrderSnapshot(
                pedidoId,
                request.produtoId(),
                request.quantidade(),
                request.clienteCpf(),
                request.clienteNome(),
                OrderStatus.PROCESSING,
                now
        ));

        redisStore.putOrder(cacheKey(pedidoId), trackingStore.findById(pedidoId).orElseThrow(), CACHE_TTL);

        orderPublisher.publish(new OrderCreatedEvent(
                pedidoId,
                request.produtoId(),
                request.quantidade(),
                request.clienteCpf(),
                request.clienteNome(),
                now
        ));

        return new OrderProcessingTicket(pedidoId, OrderStatus.PROCESSING.name(), now);
    }

    public Optional<OrderSnapshot> getOrder(UUID pedidoId) {
        String key = cacheKey(pedidoId);
        Optional<OrderSnapshot> cached = redisStore.findOrder(key);
        if (cached.isPresent()) {
            return cached;
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<OrderSnapshot> order = orderQueryGateway.findById(pedidoId);
            if (order.isPresent()) {
                OrderSnapshot merged = mergeSnapshots(trackingStore.findById(pedidoId).orElse(null), order.get());
                redisStore.putOrder(key, merged, CACHE_TTL);
                trackingStore.upsert(merged);
                return Optional.of(merged);
            }
            sleepSmallInterval();
        }

        return trackingStore.findById(pedidoId);
    }

    public List<OrderSnapshot> getOrders() {
        for (int attempt = 0; attempt < FAILURE_STATUS_ATTEMPTS; attempt++) {
            List<OrderSnapshot> orders = loadOrders();
            if (!hasPendingFailure(orders)) {
                return orders;
            }
            sleepSmallInterval();
        }
        return loadOrders();
    }

    private List<OrderSnapshot> loadOrders() {
        Map<UUID, OrderSnapshot> orders = new HashMap<>();
        trackingStore.findAll().forEach(order ->
                orders.merge(order.pedidoId(), order, OrderService::mergeSnapshots));
        orderQueryGateway.findAll().forEach(order ->
                orders.merge(order.pedidoId(), order, OrderService::mergeSnapshots));
        return orders.values().stream()
                .sorted(Comparator.comparing(OrderSnapshot::dataHoraAtualizacao).reversed())
                .toList();
    }

    private boolean hasPendingFailure(List<OrderSnapshot> orders) {
        return orders.stream()
                .anyMatch(order -> order.status() == OrderStatus.PROCESSING
                        && order.produtoId().startsWith("FAIL"));
    }

    private static OrderSnapshot mergeSnapshots(OrderSnapshot current, OrderSnapshot incoming) {
        if (current == null) {
            return incoming;
        }
        if (current.status() != OrderStatus.PROCESSING && incoming.status() == OrderStatus.PROCESSING) {
            return current;
        }
        if (current.status() == OrderStatus.PROCESSING && incoming.status() != OrderStatus.PROCESSING) {
            return incoming;
        }
        return incoming.dataHoraAtualizacao().isAfter(current.dataHoraAtualizacao())
                ? incoming
                : current;
    }

    private void sleepSmallInterval() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String cacheKey(UUID pedidoId) {
        return "order:" + pedidoId;
    }
}
