# Phase 3 — Use Cases (Business Logic)

## References

`copilot/architecture.md` - application layer scope, framework-free constraint, data flow 
- `copilot/battle-plan.md` — phase scope and business rule ordering
- `copilot/challenge.md` — contracting rules: AGRO validation, PJ record, eligibility check
- `copilot/design.md` — SRP, OCP, DIP, component boundaries, coding conventions
- `copilot/hurdles.md` — non-negotiable rules

---

## Goal

Implement the in-port interfaces defined in Phase 2 as plain Java classes.
These classes contain all business logic — no Spring, JPA, or HTTP dependencies.

The application layer is framework-free. Use case classes receive their dependencies
through constructor injection. Spring wiring is deferred to `config` (noted at the end).

When this phase is complete, the use case logic must be fully testable
with pure JUnit and Mockito — no application context required.

---

## Package root

```
br.com.sicredi.creditservice.application.usecase
```

---

## Step 1 — ContratarOperacaoCreditoService

**File:** `application/usecase/ContratarOperacaoCreditoService.java`

Implements `ContratarOperacaoCreditoUseCase`. Orchestrates all contracting rules
in the exact order defined in the battle-plan.

```java
package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.OperacaoSocio;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;

import java.time.LocalDateTime;
import java.util.UUID;

public class ContratarOperacaoCreditoService implements ContratarOperacaoCreditoUseCase {

    private final OperacaoCreditoRepository operacaoCreditoRepository;
    private final OperacaoSocioRepository operacaoSocioRepository;
    private final ProdutoElegibilidadePort produtoElegibilidadePort;

    public ContratarOperacaoCreditoService(
            OperacaoCreditoRepository operacaoCreditoRepository,
            OperacaoSocioRepository operacaoSocioRepository,
            ProdutoElegibilidadePort produtoElegibilidadePort) {
        this.operacaoCreditoRepository = operacaoCreditoRepository;
        this.operacaoSocioRepository = operacaoSocioRepository;
        this.produtoElegibilidadePort = produtoElegibilidadePort;
    }

    @Override
    public UUID executar(ContratoOperacaoCommand command) {
        validarAgro(command);
        verificarElegibilidade(command);

        OperacaoCredito operacao = new OperacaoCredito(
                UUID.randomUUID(),
                command.idAssociado(),
                command.valorOperacao(),
                command.segmento(),
                command.codigoProdutoCredito(),
                command.codigoConta(),
                command.areaBeneficiadaHa(),
                LocalDateTime.now()
        );

        OperacaoCredito salva = operacaoCreditoRepository.save(operacao);

        if (command.segmento() == Segmento.PJ) {
            operacaoSocioRepository.save(new OperacaoSocio(salva.idOperacaoCredito(), command.idAssociado()));
        }

        return salva.idOperacaoCredito();
    }

    private void validarAgro(ContratoOperacaoCommand command) {
        if (command.segmento() == Segmento.AGRO
                && (command.areaBeneficiadaHa() == null || command.areaBeneficiadaHa().signum() <= 0)) {
            throw new AgroSemAreaException();
        }
    }

    private void verificarElegibilidade(ContratoOperacaoCommand command) {
        boolean permitido = produtoElegibilidadePort.verificar(
                command.codigoProdutoCredito(),
                command.segmento(),
                command.valorOperacao()
        );
        if (!permitido) {
            throw new ElegibilidadeNegadaException(
                    command.codigoProdutoCredito(),
                    command.segmento().name()
            );
        }
    }
}
```

### Business rule order — why it matters

1. **AGRO validation first** — pure local check, no I/O. Fail fast before any external call.
2. **Eligibility check second** — only call the external service after local rules pass.
3. **Persist credit operation third** — only after all guards are cleared.
4. **Persist PJ record last** — only if the operation was successfully saved.

### Key decisions

- `UUID.randomUUID()` and `LocalDateTime.now()` are assigned here, not in the entity or repository.
- `operacaoCreditoRepository.save()` returns the persisted record; the PJ record uses its `idOperacaoCredito` to guarantee consistency.
- `signum() <= 0` rejects both null-equivalent zeros and negative values for `areaBeneficiadaHa`.
- No `@Service` — wiring is handled by `config` (see note below).

---

## Step 2 — ConsultarOperacaoCreditoService

**File:** `application/usecase/ConsultarOperacaoCreditoService.java`

Implements `ConsultarOperacaoCreditoUseCase`. Delegates entirely to the repository port.

```java
package br.com.sicredi.creditservice.application.usecase;

import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;

import java.util.UUID;

public class ConsultarOperacaoCreditoService implements ConsultarOperacaoCreditoUseCase {

    private final OperacaoCreditoRepository operacaoCreditoRepository;

    public ConsultarOperacaoCreditoService(OperacaoCreditoRepository operacaoCreditoRepository) {
        this.operacaoCreditoRepository = operacaoCreditoRepository;
    }

    @Override
    public OperacaoCredito executar(UUID idOperacaoCredito) {
        return operacaoCreditoRepository.findById(idOperacaoCredito)
                .orElseThrow(() -> new OperacaoNaoEncontradaException(idOperacaoCredito));
    }
}
```

### Key decisions

- The use case throws `OperacaoNaoEncontradaException` — the web layer maps it to 404.
- Returns the full `OperacaoCredito` domain record. The controller maps it to a response DTO.

---

## Spring Wiring — Deferred to Phase 5

Because the application layer is framework-free, use case classes carry no `@Service` annotation.
They will be registered as Spring beans in `config/UseCaseConfig.java` during Phase 5,
once the infrastructure adapters (Phase 4) that satisfy their out-port dependencies are available.

The config class will follow this shape:

```java
// Written in Phase 5 — shown here for reference only
@Configuration
public class UseCaseConfig {

    @Bean
    public ContratarOperacaoCreditoUseCase contratarOperacaoCreditoUseCase(
            OperacaoCreditoRepository operacaoCreditoRepository,
            OperacaoSocioRepository operacaoSocioRepository,
            ProdutoElegibilidadePort produtoElegibilidadePort) {
        return new ContratarOperacaoCreditoService(
                operacaoCreditoRepository,
                operacaoSocioRepository,
                produtoElegibilidadePort);
    }

    @Bean
    public ConsultarOperacaoCreditoUseCase consultarOperacaoCreditoUseCase(
            OperacaoCreditoRepository operacaoCreditoRepository) {
        return new ConsultarOperacaoCreditoService(operacaoCreditoRepository);
    }
}
```

---

## Final package layout after Phase 3

```
br.com.sicredi.creditservice.application
 usecase
    ├── ContratarOperacaoCreditoService.java
    └── ConsultarOperacaoCreditoService.java
```

---

## Validation checklist

Before moving to Phase 4, confirm:

- [ ] Both classes compile cleanly alongside Phase 1 and Phase 2 files
- [ ] No imports from `org.springframework`, `jakarta.persistence`, or `com.fasterxml`
- [ ] No `@Service`, `@Component`, or any Spring stereotype annotation on either class
- [ ] Both classes use constructor injection only — no field injection, no setters
- [ ] `ContratarOperacaoCreditoService` enforces rule order: AGRO → eligibility → persist operation → persist PJ
- [ ] `areaBeneficiadaHa` null check uses `signum() <= 0`, not `.equals(BigDecimal.ZERO)`
- [ ] `ConsultarOperacaoCreditoService` throws `OperacaoNaoEncontradaException` on absent ID
- [ ] Neither class references `web`, `infrastructure`, or any DTO type
