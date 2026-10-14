package com.flashsale.shared;

import java.util.UUID;

public record OrderCompensationEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String motivo
) {
}

