package com.flashsale.checkout.api;

import com.flashsale.checkout.domain.OrderSnapshot;
import com.flashsale.checkout.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderAcceptedResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var ticket = orderService.createOrder(request);
        return new OrderAcceptedResponse(ticket.pedidoId(), ticket.status(), ticket.dataHoraRequisicao());
    }

    @GetMapping
    public List<OrderDetailResponse> getOrders() {
        return orderService.getOrders().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{pedidoId}")
    public OrderDetailResponse getOrder(@PathVariable UUID pedidoId) {
        OrderSnapshot snapshot = orderService.getOrder(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido nao encontrado"));

        return toResponse(snapshot);
    }

    private OrderDetailResponse toResponse(OrderSnapshot snapshot) {
        return new OrderDetailResponse(
                snapshot.pedidoId(),
                snapshot.produtoId(),
                snapshot.quantidade(),
                snapshot.clienteCpf(),
                snapshot.clienteNome(),
                snapshot.status().name(),
                snapshot.dataHoraAtualizacao()
        );
    }
}
