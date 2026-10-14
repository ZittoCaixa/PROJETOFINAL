package com.flashsale.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        LocalDateTime dataHoraCriacao
) {
}

