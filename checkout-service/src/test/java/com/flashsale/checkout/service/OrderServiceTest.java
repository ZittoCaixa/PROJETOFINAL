package com.flashsale.checkout.service;

import com.flashsale.checkout.api.CreateOrderRequest;
import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.infra.InMemoryRedisStore;
import com.flashsale.checkout.infra.OrderQueryGateway;
import com.flashsale.checkout.infra.OrderTrackingStore;
import com.flashsale.shared.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    @Test
    void shouldCreateOrderAndReserveStock() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("PROD-001:2");
        OrderTrackingStore trackingStore = new OrderTrackingStore();
        OrderService service = new OrderService(redisStore, new OrderPublisher(), pedidoId -> Optional.empty(), trackingStore);

        var ticket = service.createOrder(new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto"));

        assertThat(ticket.status()).isEqualTo("PROCESSING");
        assertThat(redisStore.getStock("PROD-001")).isZero();
    }

    @Test
    void shouldReturnConflictWhenStockIsInsufficient() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("PROD-001:1");
        OrderService service = new OrderService(redisStore, new OrderPublisher(), pedidoId -> Optional.empty(), new OrderTrackingStore());

        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void shouldReturnAllCreatedOrdersNewestFirst() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("PROD-001:10,PROD-002:10");
        OrderTrackingStore trackingStore = new OrderTrackingStore();
        OrderService service = new OrderService(redisStore, new OrderPublisher(), pedidoId -> Optional.empty(), trackingStore);

        service.createOrder(new CreateOrderRequest("PROD-001", 1, "50329291076", "Fernando Augusto"));
        service.createOrder(new CreateOrderRequest("PROD-002", 1, "50329291076", "Fernando Augusto"));

        var orders = service.getOrders();

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).dataHoraAtualizacao())
                .isAfterOrEqualTo(orders.get(1).dataHoraAtualizacao());
    }

    @Test
    void shouldReturnFromGatewayAndCache() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("PROD-001:10");
        UUID orderId = UUID.randomUUID();
        OrderQueryGateway gateway = pedidoId -> Optional.of(new OrderSnapshot(
                orderId,
                "PROD-001",
                1,
                "50329291076",
                "Fernando Augusto",
                OrderStatus.CONFIRMED,
                LocalDateTime.now()
        ));
        OrderService service = new OrderService(redisStore, new OrderPublisher(), gateway, new OrderTrackingStore());

        Optional<OrderSnapshot> first = service.getOrder(orderId);
        Optional<OrderSnapshot> second = service.getOrder(orderId);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
