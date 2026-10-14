😎 Aluno/Empregado: RAMON MARQUES COUTINHO
MATRICULA: C078969-0
Assistente Pleno
CESOB

# Flash Sale Checkout - MVP

Projeto Java com dois microsservicos para checkout e processamento assincorno de pedidos de flash sale.

## O que foi implementado

- `checkout-service`
  - `POST /v1/orders` com validacao de entrada e retorno `202`
  - Reserva atomica de estoque em store Redis-like embutida
  - Publicacao assincrona de evento na fila `order-created`
  - `GET /v1/orders` para listar todos os pedidos criados
  - `GET /v1/orders/{pedido_id}` com cache-aside e 3 tentativas antes de `404`
  - Consumer da fila `order-compensation` para devolver estoque
- `order-processor-service`
  - Consumer da fila `order-created`
  - Persistencia do pedido em banco H2 embedded
  - Status `CONFIRMED` no sucesso e `CANCELLED` em falha
  - Publicacao de compensacao na fila `order-compensation`

## Arquitetura tecnica

- Java 17
- Spring Boot 3
- H2 embedded (banco do order-processor)
- Barramento de mensageria embedded com filas nomeadas como RabbitMQ (`order-created` e `order-compensation`)
- Store em memoria com comportamento Redis-like para estoque e cache com TTL

## Como executar

```bash
mvn clean test
mvn -pl order-processor-service spring-boot:run
mvn -pl checkout-service spring-boot:run
```

Servicos:

- Checkout: `http://localhost:8083`
- Processor: `http://localhost:8082`

## Exemplo de criacao de pedido

```bash
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-001",
    "quantidade":2,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'
```

## Exemplo de consulta

```bash
curl http://localhost:8083/v1/orders/{pedido_id}
```

## Exemplo de listagem

```bash
curl http://localhost:8083/v1/orders
```

O checkout consulta o endpoint interno `GET http://localhost:8082/internal/orders`
para recuperar os pedidos persistidos pelo `order-processor-service`. Pedidos que
ainda estão em processamento permanecem disponíveis no tracking local até serem
persistidos pelo processor.

## Simulacao de falha

Use um `produtoId` com prefixo `FAIL` para acionar cancelamento e compensacao:

```json
{
  "produtoId": "FAIL-001",
  "quantidade": 1,
  "clienteCpf": "50329291076",
  "clienteNome": "Fernando Augusto"
}
```
