# Architecture — Credit Service

## Architectural Style

The service follows **Clean Architecture** (Ports & Adapters / Hexagonal), organized in concentric layers where dependencies always point inward — from infrastructure toward the domain.

```
┌──────────────────────────────────────────────┐
│  Infrastructure (Spring, JPA, HTTP clients)   │
│  ┌────────────────────────────────────────┐   │
│  │  Application (use cases, ports)        │   │
│  │  ┌──────────────────────────────────┐  │   │
│  │  │  Domain (entities, rules, ports) │  │   │
│  │  └──────────────────────────────────┘  │   │
│  └────────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

No outer layer imports from an inner layer's implementation. The domain and application layers are framework-free.

---

## Package Structure

```
br.com.sicredi.creditservice
├── domain
│   ├── model              # Entities and value objects
│   └── port               # Interfaces consumed by use cases (driven ports)
│       ├── out            # Persistence and external system contracts
│       └── in             # Use case interfaces (driving ports)
├── application
│   └── usecase            # Orchestrate domain rules; depend only on ports
├── infrastructure
│   ├── persistence        # JPA repositories, entity mappers
│   └── integration
│       └── produtos       # HTTP client for the product eligibility service
├── web
│   ├── controller         # REST controllers (driving adapters)
│   └── dto                # Request / response DTOs
└── config                 # Spring configuration, bean wiring
```

---

## Key Architecture Decisions

### 1 — Domain isolation via ports

The use case layer never references Spring, JPA, or HTTP classes. All I/O is expressed through port interfaces:

- `OperacaoCreditoRepository` (out port) — persistence
- `ProdutoElegibilidadePort` (out port) — product eligibility check
- `ContratarOperacaoCreditoUseCase` (in port) — entry point for contracting

This keeps the domain testable without any framework context.

### 2 — Product eligibility as an anti-corruption layer

The external `produtos-credito` service belongs to a different bounded context. It is wrapped by `ProdutoElegibilidadePort` and implemented in `infrastructure.integration.produtos`. The domain sees only a boolean result; all HTTP and serialization concerns are confined to the adapter.

### 3 — Resilience at the adapter, not the domain

The HTTP adapter for the product service applies:
- **Circuit Breaker** — opens after repeated failures, preventing cascade
- **Retry with exponential backoff** — tolerates transient intermittency
- **Timeout** — avoids blocking threads on a slow upstream

Implemented via **Resilience4j** (`@CircuitBreaker`, `@Retry`). When the circuit is open, the adapter throws a domain exception that the use case maps to a 503 response.

### 4 — No message broker for the eligibility check

The eligibility check is synchronous and blocking — the operation cannot be contracted without its result. A Kafka or RabbitMQ bridge would not be appropriate here because:

- The frontend requires an immediate response.
- There is no decoupled throughput concern between the two services.

A message broker is the right choice only for **post-contract async events** (e.g., releasing funds, audit logs), which are out of scope but anticipated as future work.

### 5 — Segment-specific rules are domain concerns

AGRO validation (`areaBeneficiadaHa > 0`) and PJ additional record persistence are enforced inside the use case, not in the controller or entity. They are expressed as guard conditions before the persistence step.

---

## Bounded Contexts

| Context | Responsibility | Location |
|---|---|---|
| Credit Contracting | Business rules, persistence, API | `credit-service` (this service) |
| Product Catalog | Eligibility rules per product/segment | External (`produtos-credito` service) |

The two contexts communicate through the `ProdutoElegibilidadePort` interface. The external service's data model never leaks into the domain.

---

## Data Flow — Contract a Credit Operation

```
POST /operacoes-credito
        │
        ▼
  CreditController          (web layer — validates HTTP, delegates to use case)
        │
        ▼
  ContratarOperacaoUseCase  (application layer — orchestrates rules)
        ├── [AGRO] validate areaBeneficiadaHa
        ├── ProdutoElegibilidadePort.verificar(...)
        │         └── ProdutoCreditoAdapter → GET produtos-credito/...
        ├── OperacaoCreditoRepository.save(...)
        └── [PJ] OperacaoSocioRepository.save(...)
        │
        ▼
  returns idOperacaoCredito → 201 Created
```

---

## Error Handling

| Condition | HTTP Status |
|---|---|
| AGRO without `areaBeneficiadaHa` | 422 Unprocessable Entity |
| Product service returns `permiteContratar: false` | 422 Unprocessable Entity |
| Product service unavailable (circuit open / timeout) | 503 Service Unavailable |
| Operation not found (GET) | 404 Not Found |

---

## Future Improvements

- **Async post-contract events** — after contracting, publish a domain event (e.g., `OperacaoContratada`) to a message broker for downstream consumers (fund release, audit).
- **Retry idempotency** — add an idempotency key to the POST endpoint to safely handle client retries.
- **Observability** — expose circuit breaker state and retry metrics via Actuator + Micrometer.
- **Product eligibility caching** — cache eligibility responses with a short TTL to reduce coupling on the external service's availability.
- **Outbox pattern** — if domain events are introduced, use the transactional outbox pattern to guarantee at-least-once delivery without distributed transactions.
