package com.flashsale.checkout.api;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderAcceptedResponse(
        UUID pedidoId,
        String status,
        LocalDateTime dataHoraRequisicao
) {
}

