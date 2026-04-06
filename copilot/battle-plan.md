# Battle Plan — Credit Service

## Goal

Deliver the Credit Acquisition API following Clean Architecture (Ports & Adapters).
Build from the inside out: domain → ports → use cases → adapters → web.
Each phase produces a self-contained, testable unit before the next begins.

---

## Phase 1 — Domain Model

Define the core business entities as plain Java classes with no framework dependencies.

- `OperacaoCredito` — represents the credit operation (all business fields + generated ID + timestamp)
- `OperacaoSocio` — represents the PJ beneficiary link (`idOperacaoCredito` + `idAssociado`)
- `Segmento` — enum: `PF`, `PJ`, `AGRO`
- Domain exceptions: `ElegibilidadeNegadaException`, `AgroSemAreaException`, `OperacaoNaoEncontradaException`, `ProdutoServiceIndisponivelException`

---

## Phase 2 — Ports (Contracts)

Define the interfaces that isolate the use case from infrastructure. No implementations yet.

**Out-ports** (`domain.port.out`):
- `OperacaoCreditoRepository` — save and find by ID
- `OperacaoSocioRepository` — save
- `ProdutoElegibilidadePort` — check if a product allows contracting for a given segment and value

**In-port** (`domain.port.in`):
- `ContratarOperacaoCreditoUseCase` — entry point for the contracting flow
- `ConsultarOperacaoCreditoUseCase` — entry point for querying an operation

---

## Phase 3 — Use Cases (Business Logic)

Implement the in-ports. Depend only on out-ports and domain model — no Spring, JPA, or HTTP.

**`ContratarOperacaoCreditoUseCase` implementation:**
1. If `segmento = AGRO`, assert `areaBeneficiadaHa` is present and > 0 → throw `AgroSemAreaException` otherwise
2. Call `ProdutoElegibilidadePort` → throw `ElegibilidadeNegadaException` if `permiteContratar = false`
3. Build and persist `OperacaoCredito` via repository
4. If `segmento = PJ`, build and persist `OperacaoSocio` via repository
5. Return `idOperacaoCredito`

**`ConsultarOperacaoCreditoUseCase` implementation:**
- Fetch by ID from `OperacaoCreditoRepository` → throw `OperacaoNaoEncontradaException` if absent

---

## Phase 4 — Infrastructure Adapters

Implement the out-ports. Each adapter is independently testable.

**Persistence** (`infrastructure.persistence`):
- JPA `@Entity` classes for `operacao_credito` and `operacao_socio` tables
- Spring Data repositories
- Mappers between JPA entities and domain model

**Product Service Integration** (`infrastructure.integration.produtos`):
- HTTP client (RestClient) calling `GET /produtos-credito/{codigo}/permite-contratacao`
- Map `permiteContratar` response to a boolean
- Apply Resilience4j: `@CircuitBreaker` + `@Retry` with exponential backoff + timeout
- On circuit open or timeout: throw `ProdutoServiceIndisponivelException`

---

## Phase 5 — Web Layer

Expose the use cases over HTTP. No business logic here.

- `POST /operacoes-credito` → delegates to `ContratarOperacaoCreditoUseCase` → `201 Created` with `idOperacaoCredito`
- `GET /operacoes-credito/{idOperacaoCredito}` → delegates to `ConsultarOperacaoCreditoUseCase` → `200 OK`
- Request/response DTOs (no domain model exposure)
- `@ControllerAdvice` maps domain exceptions to HTTP status codes:
  - `AgroSemAreaException` → 422
  - `ElegibilidadeNegadaException` → 422
  - `OperacaoNaoEncontradaException` → 404
  - `ProdutoServiceIndisponivelException` → 503

---

## Phase 6 — Testing

- **Unit tests**: use case rules in isolation — mock all out-ports (no Spring context)
  - AGRO rejection when `areaBeneficiadaHa` is absent or zero
  - Eligibility rejection when `permiteContratar = false`
  - PJ record created only for PJ segment
  - Happy path for each segment
- **Integration tests**: full Spring context + test database (Testcontainers or H2)
  - POST and GET endpoints end-to-end
  - WireMock stub for the product service (success, failure, timeout)
