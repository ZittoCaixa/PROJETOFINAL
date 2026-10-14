package com.flashsale.checkout.service;

import com.flashsale.checkout.api.CreateOrderRequest;
import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.infra.InMemoryRedisStore;
import com.flashsale.checkout.infra.OrderQueryGateway;
import com.flashsale.checkout.infra.OrderTrackingStore;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
        InMemoryRedisStore redisStore = new InMemoryRedisStore("FAIL-002:0");
        OrderService service = new OrderService(redisStore, new OrderPublisher(), pedidoId -> Optional.empty(), new OrderTrackingStore());

        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(
                "FAIL-002", 7, "08835961726", "Ramon Marques Coutinho")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Estoque insuficiente para o produto FAIL-002");
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

    @Test
    void shouldUpdateCachedOrderWhenCompensationCancelsIt() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("FAIL-001:1");
        OrderTrackingStore trackingStore = new OrderTrackingStore();
        OrderService service = new OrderService(
                redisStore, new OrderPublisher(), pedidoId -> Optional.empty(), trackingStore);

        var ticket = service.createOrder(new CreateOrderRequest(
                "FAIL-001", 1, "50329291076", "Fernando Augusto"));
        CompensationConsumer compensationConsumer = new CompensationConsumer(redisStore, trackingStore);

        compensationConsumer.handle(new OrderCompensationEvent(
                ticket.pedidoId(), "FAIL-001", 1, "Falha no processamento do pedido"));

        assertThat(service.getOrder(ticket.pedidoId()))
                .get()
                .extracting(OrderSnapshot::status)
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(redisStore.getStock("FAIL-001")).isEqualTo(1);
    }

    @Test
    void shouldKeepCancelledOrderWhenProcessorReturnsStaleProcessingSnapshot() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("FAIL-001:1");
        OrderTrackingStore trackingStore = new OrderTrackingStore();
        UUID orderId = UUID.randomUUID();
        LocalDateTime cancelledAt = LocalDateTime.now();
        trackingStore.upsert(new OrderSnapshot(
                orderId,
                "FAIL-001",
                1,
                "50329291076",
                "Fernando Augusto",
                OrderStatus.CANCELLED,
                cancelledAt
        ));
        OrderQueryGateway staleGateway = pedidoId -> Optional.of(new OrderSnapshot(
                orderId,
                "FAIL-001",
                1,
                "50329291076",
                "Fernando Augusto",
                OrderStatus.PROCESSING,
                cancelledAt.plusSeconds(1)
        ));
        OrderService service = new OrderService(
                redisStore, new OrderPublisher(), staleGateway, trackingStore);

        assertThat(service.getOrders())
                .singleElement()
                .extracting(OrderSnapshot::status)
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldWaitForPendingFailureToReachTerminalStatusBeforeListingOrders() {
        InMemoryRedisStore redisStore = new InMemoryRedisStore("FAIL-002:0");
        OrderTrackingStore trackingStore = new OrderTrackingStore();
        UUID orderId = UUID.randomUUID();
        LocalDateTime requestedAt = LocalDateTime.now();
        OrderSnapshot processing = new OrderSnapshot(
                orderId,
                "FAIL-002",
                7,
                "08835961726",
                "Ramon Marques Coutinho",
                OrderStatus.PROCESSING,
                requestedAt
        );
        OrderSnapshot cancelled = new OrderSnapshot(
                orderId,
                "FAIL-002",
                7,
                "08835961726",
                "Ramon Marques Coutinho",
                OrderStatus.CANCELLED,
                requestedAt.plusSeconds(1)
        );
        trackingStore.upsert(processing);
        AtomicInteger processorCalls = new AtomicInteger();
        OrderQueryGateway gateway = new OrderQueryGateway() {
            @Override
            public Optional<OrderSnapshot> findById(UUID pedidoId) {
                return Optional.empty();
            }

            @Override
            public List<OrderSnapshot> findAll() {
                return processorCalls.incrementAndGet() < 2 ? List.of() : List.of(cancelled);
            }
        };
        OrderService service = new OrderService(
                redisStore, new OrderPublisher(), gateway, trackingStore);

        assertThat(service.getOrders())
                .singleElement()
                .extracting(OrderSnapshot::status)
                .isEqualTo(OrderStatus.CANCELLED);
    }
}
