package com.flashsale.processor.service;

import com.flashsale.processor.domain.OrderEntity;
import com.flashsale.processor.domain.OrderRepository;
import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderCreatedEvent;
import com.flashsale.shared.OrderStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderProcessorService {

    private final OrderRepository orderRepository;

    public OrderProcessorService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    void subscribe() {
        EmbeddedRabbitBroker.getInstance().subscribe("order-created", message -> {
            if (message instanceof OrderCreatedEvent event) {
                process(event);
            }
        });
    }

    public void process(OrderCreatedEvent event) {
        boolean fail = shouldFail(event);
        OrderEntity entity = orderRepository.findById(event.pedidoId()).orElseGet(OrderEntity::new);

        entity.setPedidoId(event.pedidoId());
        entity.setProdutoId(event.produtoId());
        entity.setQuantidade(event.quantidade());
        entity.setClienteCpf(event.clienteCpf());
        entity.setClienteNome(event.clienteNome());
        entity.setDataHoraAtualizacao(LocalDateTime.now());

        if (fail) {
            entity.setStatus(OrderStatus.CANCELLED.name());
            orderRepository.save(entity);
            EmbeddedRabbitBroker.getInstance().publish("order-compensation", new OrderCompensationEvent(
                    event.pedidoId(),
                    event.produtoId(),
                    event.quantidade(),
                    "Falha no processamento do pedido"
            ));
            return;
        }

        entity.setStatus(OrderStatus.CONFIRMED.name());
        orderRepository.save(entity);
    }

    public Optional<OrderEntity> findById(UUID pedidoId) {
        return orderRepository.findById(pedidoId);
    }

    public List<OrderEntity> findAll() {
        return orderRepository.findAll();
    }

    private boolean shouldFail(OrderCreatedEvent event) {
        return event.produtoId().startsWith("FAIL");
    }
}
