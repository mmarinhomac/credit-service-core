# Implementation Plan — Credit Service

## Phase 1 — Data Layer

- Define the JPA entity for the credit operation (`operacao_credito` table)
  - Fields: `idOperacaoCredito` (UUID, generated), all request fields, `dataContratacao` (timestamp)
- Define the JPA entity for the PJ beneficiary link (`operacao_socio` table)
  - Fields: `idOperacaoCredito`, `idAssociado`
- Create the repositories for both entities

## Phase 2 — Product Service Integration

- Create an HTTP client (RestClient or WebClient) to call the external product eligibility endpoint
- Map the `permiteContratar` response
- Handle service failures (timeout, unavailability) with a clear error response

## Phase 3 — Business Logic

- Implement the contracting rules in a service layer:
  1. If `segmento = AGRO`, validate that `areaBeneficiadaHa` is present and greater than zero — reject otherwise
  2. Call the product service to check eligibility — reject if `permiteContratar = false`
  3. Persist the credit operation
  4. If `segmento = PJ`, persist the additional beneficiary record
  5. Return `idOperacaoCredito`

## Phase 4 — API Layer

- `POST /operacoes-credito` — contract a new credit operation
- `GET /operacoes-credito/{idOperacaoCredito}` — retrieve a credit operation by ID
- Map request/response DTOs (no entity exposure)
- Return appropriate HTTP status codes (201, 200, 422 for business rejections)

## Phase 5 — Testing

- Unit tests for the business rules (AGRO validation, PJ record, eligibility check)
- Integration test for each endpoint using a test database
- Mock the external product service in tests
