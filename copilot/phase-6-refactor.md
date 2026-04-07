# Phase 6 Refactor — Remove Smoke Tests

## References

- [`copilot/architecture.md`](./architecture.md) — Clean Architecture layers and adapter boundaries
- [`copilot/battle-plan.md`](./battle-plan.md) — Phase 6 testing strategy (unit + integration with WireMock stubs)
- [`copilot/challenge.md`](./challenge.md) — Original challenge spec and the real product service URL
- [`copilot/design.md`](./design.md) — Stack, conventions, and component boundaries
- [`copilot/hurdles.md`](./hurdles.md) — Non-negotiable project rules

---

## Problem Statement

`OperacaoCreditoSmokeIT` hits `desafio-credito-sicredi.wiremockapi.cloud` — the real external product service from the challenge spec — over the public internet. This was introduced as a convenience smoke test but violates the testing strategy defined in [`battle-plan.md`](./battle-plan.md), which mandates WireMock stubs for all integration test scenarios.

These tests are fragile by design: they depend on external availability, introduce network latency, pollute the remote service's data (each `POST` creates a real DB record inside the sandbox), and cannot be run in offline or CI environments.

All scenarios covered by the smoke tests already have equivalent coverage in `OperacaoCreditoIT` (WireMock + Testcontainers). The smoke tests add no unique assertion value.

---

## Scope

**Remove:**
- `backend/credit-service/src/test/java/br/com/sicredi/creditservice/OperacaoCreditoSmokeIT.java`
- `backend/credit-service/src/test/resources/application-smoke.yml`

**Do not touch:**
- `OperacaoCreditoIT` — correct, self-contained, no external dependencies
- `ContratarOperacaoCreditoServiceTest` — unit tests, no infrastructure
- `ConsultarOperacaoCreditoServiceTest` — unit tests, no infrastructure
- `CreditServiceApplicationTests` — basic context-load sanity check
- All production source files

---

## Why the Smoke Profile Config Also Goes Away

`application-smoke.yml` exists solely to support `OperacaoCreditoSmokeIT`:

```yaml
# src/test/resources/application-smoke.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:15:///creditdb          # Testcontainers JDBC URL
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: create-drop

# produtos-credito.base-url is NOT overridden here →
# inherits https://desafio-credito-sicredi.wiremockapi.cloud from application.yml
```

With the test class gone, no Spring profile named `smoke` is ever activated. The YAML file is dead config and should be removed to avoid confusion.

---

## Detailed Refactor Steps

### Step 1 — Delete the smoke test class

Delete the file:
```
backend/credit-service/src/test/java/br/com/sicredi/creditservice/OperacaoCreditoSmokeIT.java
```

The file contains five test methods, all tagged `@Tag("smoke")` and backed by `@ActiveProfiles("smoke")`. After deletion, no class in the test tree references the `smoke` tag or profile.

### Step 2 — Delete the smoke Spring profile config

Delete the file:
```
backend/credit-service/src/test/resources/application-smoke.yml
```

This file has no other consumers. Deleting it removes the only path through which the real external URL could accidentally be used in tests.

### Step 3 — Verify no remaining references

Search the test source tree for any leftover references before considering the work done:

```bash
grep -r "smoke" backend/credit-service/src/test/
grep -r "wiremockapi.cloud" backend/credit-service/src/test/
```

Both commands must produce no output.

### Step 4 — Run the test suite

Execute the full test suite to confirm no regressions:

```bash
cd backend/credit-service
mvn test
```

Expected outcome: all tests in `OperacaoCreditoIT`, `ContratarOperacaoCreditoServiceTest`, `ConsultarOperacaoCreditoServiceTest`, and `CreditServiceApplicationTests` pass. Docker must be running for Testcontainers (PostgreSQL) to start.

---

## Test Coverage After Refactor

The table below maps every smoke test scenario to its surviving equivalent.

| Removed smoke test | Covered by |
|---|---|
| `postPF_realExternalService_returns201` | `OperacaoCreditoIT#shouldContractPfOperacao` |
| `postPJ_realExternalService_returns201` | `OperacaoCreditoIT#shouldContractPjOperacaoAndPersistSocioRecord` |
| `postAGRO_realExternalService_returns201` | `OperacaoCreditoIT#shouldContractAgroOperacao` |
| `postPF_valueOutOfRange_realServiceDenies_returns422` | `OperacaoCreditoIT#shouldReturn422WhenEligibilityDenied` |
| `postAGRO_missingArea_returns422` | `OperacaoCreditoIT#shouldReturn422WhenAgroHasNoArea` |

No scenario is left without coverage. The round-trip GET assertion from `postPF_realExternalService_returns201` is also covered by `OperacaoCreditoIT#shouldReturnOperacaoById`.

---

## Consistency Notes

- **`architecture.md`** — The adapter boundary for the product service (`ProdutoElegibilidadePort`) is unchanged. The refactor only removes tests; no production code is modified.
- **`battle-plan.md` Phase 6** — The intended test strategy is WireMock for the product service and Testcontainers for the database. After this refactor, the test suite exactly matches that strategy.
- **`challenge.md`** — The real URL (`desafio-credito-sicredi.wiremockapi.cloud`) is the upstream service described in the challenge spec. It remains referenced in `application.yml` as the production default, which is correct. It is removed only from the test configuration.
- **`design.md`** — No package structure or component boundary changes. The refactor is a pure test file removal.
- **`hurdles.md`** — Do not commit. Generate and validate only; the developer owns all version control actions.
