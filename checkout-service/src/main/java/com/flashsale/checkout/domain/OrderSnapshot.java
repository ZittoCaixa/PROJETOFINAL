package com.flashsale.checkout.domain;

import com.flashsale.shared.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderSnapshot(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao
) {
}

