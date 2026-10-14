package com.flashsale.checkout.infra;

import com.flashsale.checkout.domain.OrderSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderProcessorHttpGateway implements OrderQueryGateway {

    private final RestTemplate restTemplate;
    private final String processorBaseUrl;

    public OrderProcessorHttpGateway(
            RestTemplate restTemplate,
            @Value("${app.processor.base-url:http://localhost:8082}") String processorBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.processorBaseUrl = processorBaseUrl;
    }

    @Override
    public Optional<OrderSnapshot> findById(UUID pedidoId) {
        String url = processorBaseUrl + "/internal/orders/" + pedidoId;
        try {
            var response = restTemplate.getForEntity(url, ProcessorOrderResponse.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            var body = response.getBody();
            return Optional.of(body.toSnapshot());
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<OrderSnapshot> findAll() {
        String url = processorBaseUrl + "/internal/orders";
        try {
            var response = restTemplate.getForEntity(url, ProcessorOrderResponse[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            return Arrays.stream(response.getBody())
                    .map(ProcessorOrderResponse::toSnapshot)
                    .toList();
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private record ProcessorOrderResponse(
            UUID pedidoId,
            String produtoId,
            int quantidade,
            String clienteCpf,
            String clienteNome,
            String status,
            java.time.LocalDateTime dataHoraAtualizacao
    ) {
        private OrderSnapshot toSnapshot() {
            return new OrderSnapshot(
                    pedidoId,
                    produtoId,
                    quantidade,
                    clienteCpf,
                    clienteNome,
                    com.flashsale.shared.OrderStatus.valueOf(status),
                    dataHoraAtualizacao
            );
        }
    }
}
