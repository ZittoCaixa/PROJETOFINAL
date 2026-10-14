package com.flashsale.checkout.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderProcessingTicket(
        UUID pedidoId,
        String status,
        LocalDateTime dataHoraRequisicao
) {
}

