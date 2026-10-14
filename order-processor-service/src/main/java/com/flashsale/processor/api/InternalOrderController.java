package com.flashsale.processor.api;

import com.flashsale.processor.service.OrderProcessorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import com.flashsale.shared.OrderCreatedEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderProcessorService orderProcessorService;

    public InternalOrderController(OrderProcessorService orderProcessorService) {
        this.orderProcessorService = orderProcessorService;
    }

    @GetMapping("/{pedidoId}")
    public OrderResponse findById(@PathVariable UUID pedidoId) {
        var entity = orderProcessorService.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));

        return new OrderResponse(
                entity.getPedidoId(),
                entity.getProdutoId(),
                entity.getQuantidade(),
                entity.getClienteCpf(),
                entity.getClienteNome(),
                entity.getStatus(),
                entity.getDataHoraAtualizacao()
        );
    }

    @PostMapping
    public OrderResponse receive(@RequestBody OrderCreatedEvent event) {
        orderProcessorService.process(event);
        return findById(event.pedidoId());
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderProcessorService.findAll().stream()
                .map(entity -> new OrderResponse(
                        entity.getPedidoId(),
                        entity.getProdutoId(),
                        entity.getQuantidade(),
                        entity.getClienteCpf(),
                        entity.getClienteNome(),
                        entity.getStatus(),
                        entity.getDataHoraAtualizacao()
                ))
                .toList();
    }
}
