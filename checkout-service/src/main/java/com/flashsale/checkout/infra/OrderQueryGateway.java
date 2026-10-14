package com.flashsale.checkout.infra;

import com.flashsale.checkout.domain.OrderSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderQueryGateway {
    Optional<OrderSnapshot> findById(UUID pedidoId);

    default List<OrderSnapshot> findAll() {
        return List.of();
    }
}
