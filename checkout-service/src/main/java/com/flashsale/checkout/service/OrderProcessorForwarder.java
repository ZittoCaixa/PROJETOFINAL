package com.flashsale.checkout.service;

import com.flashsale.shared.EmbeddedRabbitBroker;
import com.flashsale.shared.OrderCompensationEvent;
import com.flashsale.shared.OrderCreatedEvent;
import com.flashsale.shared.OrderStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * O broker embarcado vive dentro de cada JVM, entao o evento "order-created" nao chega ao
 * order-processor-service (outro processo). Este componente encaminha o evento via HTTP e,
 * se o processamento falhar, dispara a compensacao localmente.
 */
@Component
public class OrderProcessorForwarder {

    private final RestTemplate restTemplate;
    private final String processorBaseUrl;

    public OrderProcessorForwarder(
            RestTemplate restTemplate,
            @Value("${app.processor.base-url:http://localhost:8082}") String processorBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.processorBaseUrl = processorBaseUrl;
    }

    @PostConstruct
    void subscribe() {
        EmbeddedRabbitBroker.getInstance().subscribe("order-created", message -> {
            if (message instanceof OrderCreatedEvent event) {
                forward(event);
            }
        });
    }

    void forward(OrderCreatedEvent event) {
        try {
            var response = restTemplate.postForEntity(
                    processorBaseUrl + "/internal/orders", event, Map.class);
            Object status = response.getBody() == null ? null : response.getBody().get("status");
            if (OrderStatus.CANCELLED.name().equals(status)) {
                EmbeddedRabbitBroker.getInstance().publish("order-compensation", new OrderCompensationEvent(
                        event.pedidoId(),
                        event.produtoId(),
                        event.quantidade(),
                        "Falha no processamento do pedido"
                ));
            }
        } catch (RestClientException ex) {
            // processador indisponivel: pedido segue PROCESSING
        }
    }
}
