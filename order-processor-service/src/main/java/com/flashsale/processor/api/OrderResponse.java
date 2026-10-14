package com.flashsale.processor.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        String status,
        LocalDateTime dataHoraAtualizacao
) {
}

