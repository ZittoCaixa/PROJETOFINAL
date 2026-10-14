# 🧪 GUIA PRÁTICO DE VALIDAÇÃO - 15 Passos do MVP

## Como Validar Cada Passo

### 1️⃣ PASSO 01: Microserviços Criados
**Validasse construindo o projeto:**
```bash
mvn clean install -DskipTests
```
**Verificar**: 4 módulos compilados com sucesso:
- ✅ `flash-sale-platform` (agregador)
- ✅ `shared-kernel` (kernel compartilhado)
- ✅ `checkout-service` (serviço 1)
- ✅ `order-processor-service` (serviço 2)

**Arquivos**:
```
pom.xml (multi-módulo)
checkout-service/pom.xml
order-processor-service/pom.xml
shared-kernel/pom.xml
```

---

### 2️⃣ PASSO 02: POST /v1/orders Implementado
**Validade**:
```bash
# Iniciar checkout-service na porta 8083
mvn -pl checkout-service spring-boot:run

# Em outro terminal, enviar requisição
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-001",
    "quantidade":2,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'
```

**Resposta esperada (202 Accepted)**:
```json
{
  "pedido_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "PROCESSING",
  "data_hora_requisicao": "2026-10-11T12:30:00"
}
```

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/api/OrderController.java`

---

### 3️⃣ PASSO 03: Validação de Dados
**Teste 1 - Produto obrigatório**:
```bash
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"",
    "quantidade":2,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'
```
**Esperado**: `400 Bad Request` - "produto_id é obrigatório"

**Teste 2 - Quantidade deve ser > 0**:
```bash
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-001",
    "quantidade":0,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'
```
**Esperado**: `400 Bad Request` - "quantidade deve ser maior que zero"

**Teste 3 - CPF obrigatório**:
```bash
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-001",
    "quantidade":2,
    "clienteCpf":"",
    "clienteNome":"Fernando Augusto"
  }'
```
**Esperado**: `400 Bad Request` - "cliente_cpf é obrigatório"

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/api/CreateOrderRequest.java`

---

### 4️⃣ PASSO 04: Redis para Estoque
**Validar estrutura de dados**:
```
# Verificar classe InMemoryRedisStore
Estrutura em memória: Map<String, StockEntry>
Chave formato: "PROD-001"
Valor: Integer (quantidade disponível)
TTL: Duration (5 minutos para cache)
```

**Teste unitário**:
```bash
mvn test -Dtest=OrderServiceTest#shouldCreateOrderAndReserveStock
```
**Esperado**: ✅ PASS

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/infra/InMemoryRedisStore.java`

---

### 5️⃣ PASSO 05: Evitar Regressão de Estoque
**Teste - Estoque insuficiente**:
```bash
mvn test -Dtest=OrderServiceTest#shouldReturnConflictWhenStockIsInsufficient
```
**Esperado**: ✅ PASS - Throws `BusinessException`

**Validar via HTTP** (com estoque limitado):
```bash
# Produto com apenas 1 unidade
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-LIMITED",
    "quantidade":5,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'
```
**Esperado**: `409 Conflict`
```json
{
  "mensagem": "Estoque insuficiente para o produto PROD-LIMITED"
}
```

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/service/OrderService.java` (linha 41)

---

### 6️⃣ PASSO 06: Publicar em Fila order-created
**Validar consumidor automático**:
```java
// Código em OrderService.java:
orderPublisher.publish(new OrderCreatedEvent(
    pedidoId,
    request.produtoId(),
    request.quantidade(),
    request.clienteCpf(),
    request.clienteNome(),
    now
));
```

**Teste integrado**:
```bash
mvn test -Dtest=OrderProcessorServiceTest#shouldConfirmOrderWhenProcessingSucceeds
```
**Esperado**: ✅ PASS - Mensagem consumida e processada

**Arquivo**: 
- `checkout-service/src/main/java/com/flashsale/checkout/service/OrderPublisher.java`
- `checkout-service/src/main/java/com/flashsale/checkout/service/OrderProcessorForwarder.java` (o broker embedded é local à JVM; este componente entrega o evento ao processor via `POST /internal/orders`)
- `shared-kernel/src/main/java/com/flashsale/shared/OrderCreatedEvent.java`

---

### 7️⃣ PASSO 07: Consumer em order-processor-service
**Validar inicialização automática**:
```java
@PostConstruct
void subscribe() {
    EmbeddedRabbitBroker.getInstance().subscribe("order-created", message -> {
        if (message instanceof OrderCreatedEvent event) {
            process(event);
        }
    });
}
```

**Teste**:
```bash
mvn test --projects order-processor-service
```
**Esperado**: ✅ 2 testes PASS

**Arquivo**: `order-processor-service/src/main/java/com/flashsale/processor/service/OrderProcessorService.java` (linha 26-31)

---

### 8️⃣ PASSO 08: Persistência em Banco H2
**Validar tabela criada**:
```
Banco: H2 (jdbc:h2:mem:processor-db)
Tabela: orders
Colunas:
  - pedidoId (UUID, PK)
  - produtoId (VARCHAR)
  - quantidade (INTEGER)
  - clienteCpf (VARCHAR)
  - clienteNome (VARCHAR)
  - status (VARCHAR)
  - dataHoraAtualizacao (TIMESTAMP)
```

**Teste**:
```bash
mvn test -Dtest=OrderProcessorServiceTest#shouldConfirmOrderWhenProcessingSucceeds
```
**Verificar no output**: "Initialized JPA EntityManagerFactory"

**Acessar console H2**:
```
URL: http://localhost:8082/h2-console
JDBC URL: jdbc:h2:mem:processor-db
```

**Arquivo**: `order-processor-service/src/main/java/com/flashsale/processor/domain/OrderEntity.java`

---

### 9️⃣ PASSO 09: Fila order-compensation
**Validar evento de compensação**:
```java
EmbeddedRabbitBroker.getInstance().publish("order-compensation", 
    new OrderCompensationEvent(
        event.pedidoId(),
        event.produtoId(),
        event.quantidade(),
        "Falha no processamento do pedido"
    )
);
```

**Teste**:
```bash
mvn test -Dtest=OrderProcessorServiceTest#shouldPublishCompensationWhenProcessingFails
```
**Esperado**: ✅ PASS

**Arquivo**: 
- `order-processor-service/src/main/java/com/flashsale/processor/service/OrderProcessorService.java` (linha 48)
- `shared-kernel/src/main/java/com/flashsale/shared/OrderCompensationEvent.java`

---

### 🔟 PASSO 10: Devolver Estoque em Falha
**Validar consumer de compensação**:
```java
@PostConstruct
void subscribe() {
    EmbeddedRabbitBroker.getInstance().subscribe("order-compensation", message -> {
        // Identifica o evento
        // Incrementa estoque
        redisStore.releaseStock(event.produtoId(), event.quantidade());
        // Atualiza status para CANCELLED
    });
}
```

**Teste integrado**:
```bash
mvn test -Dtest=OrderProcessorServiceTest#shouldPublishCompensationWhenProcessingFails
```
**Validações**:
- ✅ Status alterado para CANCELLED
- ✅ Mensagem de compensação publicada
- ✅ Estoque devolvido

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/service/CompensationConsumer.java`

---

### 1️⃣1️⃣ PASSO 11: GET /v1/orders/{pedido_id}
**Teste - Pedido encontrado (200 OK)**:
```bash
# Primeiro, criar um pedido e pegar o pedido_id
PEDIDO_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

curl -X GET http://localhost:8083/v1/orders/$PEDIDO_ID
```

**Resposta esperada**:
```json
{
  "pedido_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "produto_id": "PROD-001",
  "quantidade": 2,
  "cliente_cpf": "50329291076",
  "cliente_nome": "Fernando Augusto",
  "status": "CONFIRMED",
  "data_hora_atualizacao": "2026-10-11T12:30:00"
}
```

**Teste - Pedido não encontrado (404 Not Found)**:
```bash
curl -X GET http://localhost:8083/v1/orders/00000000-0000-0000-0000-000000000000
```

**Resposta esperada**:
```json
{
  "mensagem": "Pedido não encontrado"
}
```

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/api/OrderController.java` (linha 35-49)

---

### 1️⃣2️⃣ PASSO 12: Cache-Aside com Redis
**Fluxo:**
```
1. Requisição: GET /v1/orders/{id}
2. Buscar em Redis (cache)
   ├─ SIM: Retornar imediatamente ✅
   └─ NÃO: Seguir para passo 3
3. Consultar banco de dados
   ├─ Encontrou: Ir para passo 4
   └─ Não encontrou: Ir para passo 5
4. Armazenar em Redis com TTL 5 minutos
5. Retornar resultado
```

**Teste**:
```bash
mvn test -Dtest=OrderServiceTest#shouldReturnFromGatewayAndCache
```
**Esperado**: ✅ PASS

**Código**:
```java
public Optional<OrderSnapshot> getOrder(UUID pedidoId) {
    String key = cacheKey(pedidoId);
    // 1. Cache check
    Optional<OrderSnapshot> cached = redisStore.findOrder(key);
    if (cached.isPresent()) {
        return cached;  // Hit! Retorna do cache
    }
    
    // 2. Database lookup
    for (int attempt = 0; attempt < 3; attempt++) {
        Optional<OrderSnapshot> order = orderQueryGateway.findById(pedidoId);
        if (order.isPresent()) {
            // 3. Cache put
            redisStore.putOrder(key, order.get(), CACHE_TTL);
            return order;
        }
    }
    return Optional.empty();
}
```

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/service/OrderService.java` (linha 73-91)

---

### 1️⃣3️⃣ PASSO 13: Três Tentativas Antes de 404
**Verificar código**:
```java
for (int attempt = 0; attempt < 3; attempt++) {
    Optional<OrderSnapshot> order = orderQueryGateway.findById(pedidoId);
    if (order.isPresent()) {
        // Encontrou!
        return order;
    }
    sleepSmallInterval();  // Aguarda 100ms
}
// 3 tentativas executadas → Retorna 404
return trackingStore.findById(pedidoId);
```

**Teste**:
```bash
mvn test -Dtest=OrderServiceTest
```
**Esperado**: ✅ Todos os testes PASS

**Validação visual**:
- Tentativa 1: Aguarda 100ms
- Tentativa 2: Aguarda 100ms
- Tentativa 3: Aguarda 100ms
- **Total**: até 300ms esperando no máximo

**Arquivo**: `checkout-service/src/main/java/com/flashsale/checkout/service/OrderService.java` (linha 80-88)

---

### 1️⃣4️⃣ PASSO 14: Testes Implementados
**Total de testes: 5**

**Checkout Service (3 testes)**:
```bash
mvn test -Dtest=OrderServiceTest
```

| Teste | Descrição | Status |
|-------|-----------|--------|
| `shouldCreateOrderAndReserveStock` | Criar pedido com reserva atômica | ✅ PASS |
| `shouldReturnConflictWhenStockIsInsufficient` | Rejeitar pedido com estoque insuficiente | ✅ PASS |
| `shouldReturnFromGatewayAndCache` | Cache-Aside funcionando | ✅ PASS |

**Order Processor Service (2 testes)**:
```bash
mvn test -Dtest=OrderProcessorServiceTest
```

| Teste | Descrição | Status |
|-------|-----------|--------|
| `shouldConfirmOrderWhenProcessingSucceeds` | Processar pedido com sucesso | ✅ PASS |
| `shouldPublishCompensationWhenProcessingFails` | Publicar compensação em falha | ✅ PASS |

**Executar todos**:
```bash
mvn clean test
```

**Arquivo**: 
- `checkout-service/src/test/java/com/flashsale/checkout/service/OrderServiceTest.java`
- `order-processor-service/src/test/java/com/flashsale/processor/service/OrderProcessorServiceTest.java`

---

### 1️⃣5️⃣ PASSO 15: README.md
**Verificar existência e conteúdo**:
```bash
cat README.md
```

**Seções cobertas**:
- ✅ Descrição do projeto
- ✅ O que foi implementado
- ✅ Arquitetura técnica
- ✅ Como executar
- ✅ Exemplos de requisições
- ✅ Simulação de falhas
- ✅ Portas e endpoints

**Arquivo**: `README.md` na raiz do projeto

---

## 🎯 Validação Rápida (5 minutos)

```bash
# 1. Build e Testes
mvn clean test

# 2. Executar processor
mvn -pl order-processor-service spring-boot:run &
sleep 2

# 3. Executar checkout
mvn -pl checkout-service spring-boot:run &
sleep 2

# 4. Criar pedido
curl -X POST http://localhost:8083/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "produtoId":"PROD-001",
    "quantidade":2,
    "clienteCpf":"50329291076",
    "clienteNome":"Fernando Augusto"
  }'

# 5. Consultar pedido (usar pedido_id da resposta anterior)
curl -X GET http://localhost:8083/v1/orders/{pedido_id}
```

## ✅ Checklist Final

- ✅ 2 microsserviços criados
- ✅ POST /v1/orders implementado
- ✅ Dados validados
- ✅ Redis para estoque
- ✅ Sem regressão de estoque
- ✅ Fila order-created
- ✅ Consumer em processor
- ✅ Banco H2 com persistência
- ✅ Fila order-compensation
- ✅ Compensação de estoque
- ✅ GET /v1/orders/{pedido_id}
- ✅ Cache-Aside
- ✅ 3 tentativas antes 404
- ✅ 5 testes (todos PASS)
- ✅ README.md completo

**Status**: 🎉 **TODOS OS 15 PASSOS VALIDADOS COM SUCESSO**

