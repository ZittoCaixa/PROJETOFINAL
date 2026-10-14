package com.flashsale.processor.service;

import com.flashsale.processor.domain.OrderRepository;
import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderProcessorServiceTest {

    @Autowired
    private OrderProcessorService service;

    @Autowired
    private OrderRepository repository;

    @Test
    void shouldConfirmOrderWhenProcessingSucceeds() {
        UUID id = UUID.randomUUID();

        service.process(new OrderCreatedEvent(id, "PROD-001", 1, "50329291076", "Fernando Augusto", LocalDateTime.now()));

        var saved = repository.findById(id);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void shouldPublishCompensationWhenProcessingFails() throws InterruptedException {
        UUID id = UUID.randomUUID();
        List<OrderCompensationEvent> compensationEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        EmbeddedRabbitBroker.getInstance().subscribe("order-compensation", message -> {
            if (message instanceof OrderCompensationEvent event && event.pedidoId().equals(id)) {
                compensationEvents.add(event);
                latch.countDown();
            }
        });

        service.process(new OrderCreatedEvent(id, "FAIL-001", 1, "50329291076", "Fernando Augusto", LocalDateTime.now()));

        latch.await(2, TimeUnit.SECONDS);
        var saved = repository.findById(id);

        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo("CANCELLED");
        assertThat(compensationEvents).hasSize(1);
    }
}

