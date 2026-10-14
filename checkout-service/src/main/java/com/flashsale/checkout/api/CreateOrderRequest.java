package com.flashsale.checkout.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @JsonAlias("produtoId")
        @NotBlank(message = "produto_id e obrigatorio")
        String produtoId,
        @Min(value = 1, message = "quantidade deve ser maior que zero")
        int quantidade,
        @JsonAlias("clienteCpf")
        @NotBlank(message = "cliente_cpf e obrigatorio")
        String clienteCpf,
        @JsonAlias("clienteNome")
        @NotBlank(message = "cliente_nome e obrigatorio")
        String clienteNome
) {
}
