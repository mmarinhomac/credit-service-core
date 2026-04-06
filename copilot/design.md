# Project Design & Conventions

## Repository Structure

```
credit-service-core/
 backend/                  # All backend services
   └── <service-name>/       # One folder per service, named after the artifact
       ├── src/
       ├── pom.xml
       └── ...
 copilot/                  # Prompts, guides, and project documentation
 .gitignore
```

> When generating a new service, always extract it into `./backend/<artifact-id>/` — never directly into `./backend/`.

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Build | Maven |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Resilience | Resilience4j |

---

## Services

| Artifact ID | Path | Description |
|---|---|---|
| `credit-service` | `backend/credit-service` | Credit Acquisition API |

---

## SOLID Principles

### Single Responsibility
Each class has one reason to change. Controllers handle HTTP only. Use cases orchestrate domain rules only. Adapters translate between external systems and the domain.

### Open/Closed
Use cases depend on port interfaces, not concrete implementations. New adapters (e.g., a stub, a cached version, or an alternate HTTP client) can be added without modifying the use case.

### Liskov Substitution
Adapters implement port interfaces fully and correctly. The use case works identically whether it receives the real HTTP adapter or a test double.

### Interface Segregation
Ports are narrow and purposeful. `ProdutoElegibilidadePort` exposes only the eligibility check. `OperacaoCreditoRepository` exposes only what the use case needs — no generic repository methods are leaked into the domain.

### Dependency Inversion
High-level modules (use cases) depend on abstractions (ports). Low-level modules (JPA repositories, HTTP clients) implement those abstractions. Spring DI wires the implementations at runtime.

---

## Component Cohesion

### REP — Reuse/Release Equivalence
Classes grouped into the same package must be releasable together and make sense as a unit to reuse. In this service, `domain.model` and `domain.port` are cohesive release units — they represent the stable core that any future use case or adapter will depend on. Infrastructure packages (`persistence`, `integration`) are separate because they change for different reasons (DB schema, HTTP contract) and must not force a domain release.

### CCP — Common Closure
Classes that change together belong together. All rules that govern how a credit operation is contracted live in `application.usecase` — not split between the controller and the persistence layer. If the contracting rules change (e.g., a new segment is added), only the use case package is touched. Similarly, all product service HTTP concerns (URL, response mapping, retry config) are co-located in `infrastructure.integration.produtos`.

### CRP — Common Reuse
Do not force users of a package to depend on things they do not use. `domain.port.out` separates persistence ports (`OperacaoCreditoRepository`, `OperacaoSocioRepository`) from the integration port (`ProdutoElegibilidadePort`). A use case that only needs persistence does not transitively depend on the HTTP adapter or its Resilience4j configuration.

---

## Component Coupling

### ADP — Acyclic Dependencies
The dependency graph between packages is a directed acyclic graph (DAG). The allowed directions are:

```
web → application → domain
infrastructure → domain
config → all
```

No package depends on a package that depends back on it. `domain` has zero outgoing dependencies. Cycles are a build-time violation — if one appears, the offending class belongs in the wrong package.

### SDP — Stable Dependencies
Packages must depend in the direction of stability. Stability is measured by the number of incoming vs. outgoing dependencies (I = outgoing / (incoming + outgoing); lower I = more stable).

| Package | Stability | Rationale |
|---|---|---|
| `domain.model` | Most stable (I ≈ 0) | Many dependents, no dependencies |
| `domain.port` | Stable (I ≈ 0) | Defines contracts; nothing changes it externally |
| `application.usecase` | Moderately stable | Depends only on ports; changed when business rules change |
| `infrastructure.*` | Unstable (I ≈ 1) | Depends on external frameworks; changed by infra concerns |
| `web.*` | Unstable (I ≈ 1) | Depends on use cases; changed by API contract changes |

Unstable packages (`web`, `infrastructure`) depend on stable ones (`domain`). Never the reverse.

### SAP — Stable Abstractions
The most stable packages must be the most abstract. `domain.port` fulfills this: it contains only interfaces with no concrete implementation. `domain.model` contains pure data structures with no framework coupling. `application.usecase` defines use case interfaces (in-ports) that are stable and abstract.

Concrete implementations (adapters, controllers, JPA entities) live exclusively in the unstable outer layers. This ensures the stable core never needs to change because of a framework upgrade or an external API change.

---

## Component Boundaries

| Component | Responsibility | May Depend On | Must Not Depend On |
|---|---|---|---|
| `domain.model` | Entities and value objects | Nothing | Anything outside domain |
| `domain.port` | Contracts for I/O | `domain.model` | Application, infrastructure, web |
| `application.usecase` | Business orchestration | `domain.port`, `domain.model` | Spring, JPA, HTTP, web DTOs |
| `infrastructure.persistence` | JPA adapters | Spring Data, `domain.port` | `application`, `web` |
| `infrastructure.integration` | External HTTP adapters | Resilience4j, `domain.port` | `application`, `web` |
| `web.controller` | REST entry points | `application.usecase`, `web.dto` | `domain.model` directly |
| `web.dto` | Request/response shapes | Nothing | `domain.model` |
| `config` | Bean wiring | All layers | Business logic |

> The dependency rule is strict: nothing in an inner layer references an outer layer's type.

---

## Coding Conventions

- **Entities** are plain Java classes with no framework annotations in the domain layer. JPA annotations live on separate infrastructure-level `@Entity` classes mapped to domain objects.
- **Use cases** are defined as interfaces (in ports) and implemented once per use case class. Constructor injection only.
- **DTOs** never escape their layer. A controller maps a request DTO to a domain input; the use case returns a domain result; the controller maps that to a response DTO.
- **Exceptions** are domain-defined (e.g., `ElegibilidadeNegadaException`, `OperacaoNaoEncontradaException`). Infrastructure translates them to HTTP status codes via a `@ControllerAdvice`.
- **No static state, no service locators.** All dependencies are explicit and injected.
