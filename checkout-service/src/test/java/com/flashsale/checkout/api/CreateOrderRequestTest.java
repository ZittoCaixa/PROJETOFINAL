package com.flashsale.checkout.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void shouldDeserializeDocumentedCamelCasePayload() throws Exception {
        CreateOrderRequest request = objectMapper.readValue("""
                {
                  "produtoId": "PROD-001",
                  "quantidade": 2,
                  "clienteCpf": "50329291076",
                  "clienteNome": "Fernando Augusto"
                }
                """, CreateOrderRequest.class);

        assertThat(request).isEqualTo(new CreateOrderRequest(
                "PROD-001", 2, "50329291076", "Fernando Augusto"));
    }

    @Test
    void shouldContinueDeserializingSnakeCasePayload() throws Exception {
        CreateOrderRequest request = objectMapper.readValue("""
                {
                  "produto_id": "PROD-001",
                  "quantidade": 2,
                  "cliente_cpf": "50329291076",
                  "cliente_nome": "Fernando Augusto"
                }
                """, CreateOrderRequest.class);

        assertThat(request).isEqualTo(new CreateOrderRequest(
                "PROD-001", 2, "50329291076", "Fernando Augusto"));
    }
}
