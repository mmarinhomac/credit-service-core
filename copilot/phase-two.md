# Phase 2 — Ports (Contracts)

## References

- `copilot/architecture.md` — package structure, port naming, bounded context boundaries
- `copilot/battle-plan.md` — phase scope and sequencing
- `copilot/challenge.md` — business fields and API requirements
- `copilot/design.md` — SOLID principles, component boundaries, ISP, SAP
- `copilot/hurdles.md` — non-negotiable rules

---

## Goal

Define the port interfaces that isolate the use case layer from infrastructure.
No implementations in this phase — only contracts.

These interfaces are the stable core of the application (SAP — Stable Abstractions Principle).
Once defined, they must not change unless the business contract changes.
Infrastructure and use cases written in later phases depend on them.

When this phase is complete, the `domain.port` packages must compile cleanly
with zero framework dependencies.

---

## Package root

```
br.com.sicredi.creditservice.domain.port
 in     ← driving ports (use case interfaces + input commands)
 out    ← driven ports (persistence and integration contracts)
```

---

## Step 1 — Input Command

**File:** `domain/port/in/ContratoOperacaoCommand.java`

A plain record that carries all input data needed to contract a credit operation.
Defined alongside the in-port — it is part of the use case contract.

```java
package br.com.sicredi.creditservice.domain.port.in;

import br.com.sicredi.creditservice.domain.model.Segmento;

import java.math.BigDecimal;

public record ContratoOperacaoCommand(
        Long idAssociado,
        BigDecimal valorOperacao,
        Segmento segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa
) {}
```

**Rules:**
- `areaBeneficiadaHa` is nullable — it will be null when not sent by the frontend.
- No validation here — the use case is responsible for business guard conditions.
- This record is consumed by the controller (via DTO mapping) and passed to the use case.

---

## Step 2 — In-Port: ContratarOperacaoCreditoUseCase

**File:** `domain/port/in/ContratarOperacaoCreditoUseCase.java`

The driving port for the credit contracting flow. The controller depends on this interface,
never on the implementation.

```java
package br.com.sicredi.creditservice.domain.port.in;

import java.util.UUID;

public interface ContratarOperacaoCreditoUseCase {
    UUID executar(ContratoOperacaoCommand command);
}
```

**Rules:**
- Returns only the `idOperacaoCredito` — the minimal result the frontend needs.
- Throws domain exceptions (defined in Phase 1) — not HTTP exceptions.
- Single method — ISP: the controller depends only on what it uses.

---

## Step 3 — In-Port: ConsultarOperacaoCreditoUseCase

**File:** `domain/port/in/ConsultarOperacaoCreditoUseCase.java`

The driving port for the credit operation query flow.

```java
package br.com.sicredi.creditservice.domain.port.in;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;

import java.util.UUID;

public interface ConsultarOperacaoCreditoUseCase {
    OperacaoCredito executar(UUID idOperacaoCredito);
}
```

**Rules:**
- Returns the full `OperacaoCredito` domain record — the web layer maps it to a response DTO.
- Throws `OperacaoNaoEncontradaException` when not found (defined in Phase 1).
- Single method — ISP.

---

## Step 4 — Out-Port: OperacaoCreditoRepository

**File:** `domain/port/out/OperacaoCreditoRepository.java`

Persistence contract for credit operations. Only the methods the use case needs — no generic CRUD.

```java
package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;

import java.util.Optional;
import java.util.UUID;

public interface OperacaoCreditoRepository {
    OperacaoCredito save(OperacaoCredito operacao);
    Optional<OperacaoCredito> findById(UUID idOperacaoCredito);
}
```

**Rules:**
- `save` returns the persisted `OperacaoCredito` — allows the implementation to return a DB-enriched instance if needed.
- `findById` returns `Optional` — the use case decides what to do when absent (`OperacaoNaoEncontradaException`).
- No `delete`, `findAll`, or other methods — ISP: expose only what use cases require.

---

## Step 5 — Out-Port: OperacaoSocioRepository

**File:** `domain/port/out/OperacaoSocioRepository.java`

Persistence contract for the PJ beneficiary link record.

```java
package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.OperacaoSocio;

public interface OperacaoSocioRepository {
    void save(OperacaoSocio socio);
}
```

**Rules:**
- Single method — this record is only ever written, never queried through this port.
- Returns void — the use case has no need for the persisted result.

---

## Step 6 — Out-Port: ProdutoElegibilidadePort

**File:** `domain/port/out/ProdutoElegibilidadePort.java`

Anti-corruption layer contract for the external product catalog service.
The domain sees only a boolean — all HTTP, JSON, and retry concerns are confined to the adapter.

```java
package br.com.sicredi.creditservice.domain.port.out;

import br.com.sicredi.creditservice.domain.model.Segmento;

import java.math.BigDecimal;

public interface ProdutoElegibilidadePort {
    boolean verificar(String codigoProdutoCredito, Segmento segmento, BigDecimal valorOperacao);
}
```

**Rules:**
- Returns a primitive `boolean` — no wrapper types or response objects leak into the domain.
- When the external service is unavailable, the adapter implementation throws
  `ProdutoServiceIndisponivelException` (defined in Phase 1) before this method returns.
- The use case never catches HTTP or network exceptions — only domain exceptions.

---

## Final package layout after Phase 2

```
br.com.sicredi.creditservice.domain.port
 in
   ├── ContratoOperacaoCommand.java
   ├── ContratarOperacaoCreditoUseCase.java
   └── ConsultarOperacaoCreditoUseCase.java
 out
    ├── OperacaoCreditoRepository.java
    ├── OperacaoSocioRepository.java
    └── ProdutoElegibilidadePort.java
```

---

## Validation checklist

Before moving to Phase 3, confirm:

- [ ] All 6 files compile cleanly — no errors
- [ ] No imports from `org.springframework`, `jakarta.persistence`, or `com.fasterxml`
- [ ] `ContratoOperacaoCommand` is a record; `areaBeneficiadaHa` is `BigDecimal` (nullable)
- [ ] Both in-ports are interfaces with a single `executar` method
- [ ] Both persistence out-ports expose only the methods listed — no extra CRUD
- [ ] `ProdutoElegibilidadePort.verificar` returns primitive `boolean`, not `Boolean`
- [ ] No implementation classes in `domain.port` — interfaces and records only
