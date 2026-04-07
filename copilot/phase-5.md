# Phase 5 — Web Layer

## References

- `copilot/architecture.md` — web layer as outermost driving adapter, error handling table, data flow
- `copilot/battle-plan.md` — phase scope: controllers, DTOs, exception handler, Spring wiring
- `copilot/challenge.md` — endpoint contracts, expected HTTP status codes, response fields
- `copilot/design.md` — component boundaries: web.controller may depend on application.usecase + web.dto only; web.dto must not depend on domain.model
- `copilot/hurdles.md` — non-negotiable rules

---

## Goal

Expose the two use cases as HTTP endpoints.
Wire all use case beans via a `@Configuration` class (deferred from Phase 3).

The web layer contains zero business logic. Controllers translate HTTP ↔ use case only.
Domain exceptions are mapped to HTTP status codes in a single `@RestControllerAdvice`.

---

## Package roots

```
br.com.sicredi.creditservice
 web
   ├── controller     ← REST controllers, exception handler
   └── dto            ← request and response records
 config             ← UseCaseConfig (Spring wiring for use cases)
```

---

## Step 1 — application.yml: Jackson date format

`LocalDateTime` serializes as a timestamp array by default in Jackson.
Add the following to `application.yml` so dates serialize as ISO-8601 strings:

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

---

## Step 2 — Request DTO

**File:** `web/dto/ContratoOperacaoRequest.java`

Represents the JSON body of `POST /operacoes-credito`.
`segmento` is received as a `String` — the controller converts it to the `Segmento` enum
when building the command. DTOs never reference domain types.

```java
package br.com.sicredi.creditservice.web.dto;

import java.math.BigDecimal;

public record ContratoOperacaoRequest(
        Long idAssociado,
        BigDecimal valorOperacao,
        String segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa
) {}
```

---

## Step 3 — Response DTOs

**File:** `web/dto/ContratoOperacaoResponse.java`

Response body for `POST /operacoes-credito` — `201 Created`.

```java
package br.com.sicredi.creditservice.web.dto;

import java.util.UUID;

public record ContratoOperacaoResponse(UUID idOperacaoCredito) {}
```

---

**File:** `web/dto/OperacaoCreditoResponse.java`

Response body for `GET /operacoes-credito/{idOperacaoCredito}` — `200 OK`.
Maps all fields from the `OperacaoCredito` domain record plus the contracting timestamp.

```java
package br.com.sicredi.creditservice.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OperacaoCreditoResponse(
        UUID idOperacaoCredito,
        Long idAssociado,
        BigDecimal valorOperacao,
        String segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa,
        LocalDateTime dataContratacao
) {}
```

---

**File:** `web/dto/ErrorResponse.java`

Standard error body returned on all handled exceptions.

```java
package br.com.sicredi.creditservice.web.dto;

public record ErrorResponse(String message) {}
```

---

## Step 4 — Controller

**File:** `web/controller/OperacaoCreditoController.java`

Single controller for both endpoints. No business logic — translates HTTP ↔ use case only.

```java
package br.com.sicredi.creditservice.web.controller;

import br.com.sicredi.creditservice.domain.model.OperacaoCredito;
import br.com.sicredi.creditservice.domain.model.Segmento;
import br.com.sicredi.creditservice.domain.port.in.ContratoOperacaoCommand;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.web.dto.ContratoOperacaoRequest;
import br.com.sicredi.creditservice.web.dto.ContratoOperacaoResponse;
import br.com.sicredi.creditservice.web.dto.OperacaoCreditoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/operacoes-credito")
public class OperacaoCreditoController {

    private final ContratarOperacaoCreditoUseCase contratarUseCase;
    private final ConsultarOperacaoCreditoUseCase consultarUseCase;

    public OperacaoCreditoController(ContratarOperacaoCreditoUseCase contratarUseCase,
                                     ConsultarOperacaoCreditoUseCase consultarUseCase) {
        this.contratarUseCase = contratarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoOperacaoResponse contratar(@RequestBody ContratoOperacaoRequest request) {
        ContratoOperacaoCommand command = new ContratoOperacaoCommand(
                request.idAssociado(),
                request.valorOperacao(),
                Segmento.valueOf(request.segmento()),
                request.codigoProdutoCredito(),
                request.codigoConta(),
                request.areaBeneficiadaHa()
        );
        UUID idOperacaoCredito = contratarUseCase.executar(command);
        return new ContratoOperacaoResponse(idOperacaoCredito);
    }

    @GetMapping("/{idOperacaoCredito}")
    public OperacaoCreditoResponse consultar(@PathVariable UUID idOperacaoCredito) {
        OperacaoCredito operacao = consultarUseCase.executar(idOperacaoCredito);
        return toResponse(operacao);
    }

    private OperacaoCreditoResponse toResponse(OperacaoCredito operacao) {
        return new OperacaoCreditoResponse(
                operacao.idOperacaoCredito(),
                operacao.idAssociado(),
                operacao.valorOperacao(),
                operacao.segmento().name(),
                operacao.codigoProdutoCredito(),
                operacao.codigoConta(),
                operacao.areaBeneficiadaHa(),
                operacao.dataContratacao()
        );
    }
}
```

**Note on `Segmento` import in the controller:**
The controller imports `Segmento` only to build command the it never appears in a 
request or response DTO. This is acceptable: the boundary rule prohibits domain types
from leaking into the HTTP contract, not from being used internally during mapping.

---

## Step 5 — Global Exception Handler

**File:** `web/controller/GlobalExceptionHandler.java`

Maps every domain exception to its HTTP status code. The only place in the application
where domain exceptions are caught. No business logic here.

```java
package br.com.sicredi.creditservice.web.controller;

import br.com.sicredi.creditservice.domain.exception.AgroSemAreaException;
import br.com.sicredi.creditservice.domain.exception.ElegibilidadeNegadaException;
import br.com.sicredi.creditservice.domain.exception.OperacaoNaoEncontradaException;
import br.com.sicredi.creditservice.domain.exception.ProdutoServiceIndisponivelException;
import br.com.sicredi.creditservice.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgroSemAreaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleAgroSemArea(AgroSemAreaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ElegibilidadeNegadaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleElegibilidadeNegada(ElegibilidadeNegadaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(OperacaoNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOperacaoNaoEncontrada(OperacaoNaoEncontradaException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(ProdutoServiceIndisponivelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleProdutoServiceIndisponivel(ProdutoServiceIndisponivelException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
```

---

## Step 6 — UseCaseConfig

**File:** `config/UseCaseConfig.java`

Registers both use case implementations as Spring beans.
This is the only place where use case classes are referenced by their concrete type.
All other classes depend on the port interfaces only.

```java
package br.com.sicredi.creditservice.config;

import br.com.sicredi.creditservice.application.usecase.ConsultarOperacaoCreditoService;
import br.com.sicredi.creditservice.application.usecase.ContratarOperacaoCreditoService;
import br.com.sicredi.creditservice.domain.port.in.ConsultarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.in.ContratarOperacaoCreditoUseCase;
import br.com.sicredi.creditservice.domain.port.out.OperacaoCreditoRepository;
import br.com.sicredi.creditservice.domain.port.out.OperacaoSocioRepository;
import br.com.sicredi.creditservice.domain.port.out.ProdutoElegibilidadePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

## HTTP contract summary

| Method | Path | Request | Success Response | Error Responses |
|---|---|---|---|---|
| POST | `/operacoes-credito` | `ContratoOperacaoRequest` (JSON body) | `201` + `{ "idOperacaoCredito": "..." }` | `422`, `503` |
| GET | `/operacoes-credito/{idOperacaoCredito}` | UUID path variable | `200` + `OperacaoCreditoResponse` | `404` |

---

## Final package layout after Phase 5

```
br.com.sicredi.creditservice
 web
   ├── controller
   │   ├── OperacaoCreditoController.java
   │   └── GlobalExceptionHandler.java
   └── dto
       ├── ContratoOperacaoRequest.java
       ├── ContratoOperacaoResponse.java
       ├── OperacaoCreditoResponse.java
       └── ErrorResponse.java
 config
    ├── RestClientConfig.java        (from Phase 4)
    └── UseCaseConfig.java
```

---

## Validation checklist

Before moving to Phase 6, confirm:

- [ ] `application.yml` has `write-dates-as-timestamps: false`
- [ ] All four DTOs are records with no domain type in their fields
- [ ] `ContratoOperacaoRequest.segmento` is `String`, not `Segmento`
- [ ] `OperacaoCreditoResponse.segmento` is `String` (mapped via `.name()`)
- [ ] `POST` returns `201 Created` — not `200 OK`
- [ ] `GlobalExceptionHandler` handles all four domain exceptions with the correct HTTP status
- [ ] `UseCaseConfig` depends on port interfaces — never on adapter implementations
- [ ] Controller has no `if`, `switch`, or any conditional logic — only mapping and delegation
- [ ] No domain model type (`OperacaoCredito`, `Segmento`, `OperacaoSocio`) appears in any DTO field
