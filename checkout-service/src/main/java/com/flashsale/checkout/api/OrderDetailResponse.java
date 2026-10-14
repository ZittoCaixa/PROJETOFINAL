package com.flashsale.checkout.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderDetailResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        String status,
        LocalDateTime dataHoraAtualizacao
) {
}

