# Phase 1 — Domain Model

## Goal

Create the core business model as plain Java classes with zero framework dependencies.
No Spring, no JPA, no Jackson annotations. No outer layer may be referenced here.

When this phase is complete, the `domain` package must compile and be fully testable
with pure JUnit — no application context required.

---

## Package root

All classes in this phase live under:

```
br.com.sicredi.creditservice.domain
```

---

## Step 1 — Segmento enum

**File:** `domain/model/Segmento.java`

```java
package br.com.sicredi.creditservice.domain.model;

public enum Segmento {
    PF, PJ, AGRO
}
```

No logic. Represents the three valid credit segments from the challenge.

---

## Step 2 — OperacaoCredito

**File:** `domain/model/OperacaoCredito.java`

Represents a contracted credit operation. Immutable after creation.
Use a Java record — all fields are final, no setters needed.

**Fields and types:**

| Field | Type | Notes |
|---|---|---|
| `idOperacaoCredito` | `UUID` | System-generated; never null |
| `idAssociado` | `Long` | Member identifier |
| `valorOperacao` | `BigDecimal` | Final deposit amount; positive |
| `segmento` | `Segmento` | Enum value |
| `codigoProdutoCredito` | `String` | 3-char alphanumeric code |
| `codigoConta` | `String` | 10-digit account code; kept as String to preserve leading zeros |
| `areaBeneficiadaHa` | `BigDecimal` | Nullable; only meaningful for AGRO |
| `dataContratacao` | `LocalDateTime` | System-generated contracting timestamp |

```java
package br.com.sicredi.creditservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OperacaoCredito(
        UUID idOperacaoCredito,
        Long idAssociado,
        BigDecimal valorOperacao,
        Segmento segmento,
        String codigoProdutoCredito,
        String codigoConta,
        BigDecimal areaBeneficiadaHa,
        LocalDateTime dataContratacao
) {}
```

**Rules enforced elsewhere (not here):**
- `areaBeneficiadaHa > 0` when `segmento = AGRO` → enforced in the use case (Phase 3)
- `idOperacaoCredito` and `dataContratacao` are assigned by the use case before saving

---

## Step 3 — OperacaoSocio

**File:** `domain/model/OperacaoSocio.java`

Represents the additional PJ beneficiary record. Created only when `segmento = PJ`.

**Fields and types:**

| Field | Type | Notes |
|---|---|---|
| `idOperacaoCredito` | `UUID` | Foreign key to the credit operation |
| `idAssociado` | `Long` | The partner who requested the credit |

```java
package br.com.sicredi.creditservice.domain.model;

import java.util.UUID;

public record OperacaoSocio(
        UUID idOperacaoCredito,
        Long idAssociado
) {}
```

---

## Step 4 — Domain Exceptions

Each exception represents a distinct business or integration failure.
All extend `RuntimeException`. No Spring or HTTP concepts here — HTTP mapping happens in the web layer.

**File:** `domain/exception/AgroSemAreaException.java`

```java
package br.com.sicredi.creditservice.domain.exception;

public class AgroSemAreaException extends RuntimeException {
    public AgroSemAreaException() {
        super("Operations with segmento AGRO require areaBeneficiadaHa greater than zero.");
    }
}
```

**File:** `domain/exception/ElegibilidadeNegadaException.java`

```java
package br.com.sicredi.creditservice.domain.exception;

public class ElegibilidadeNegadaException extends RuntimeException {
    public ElegibilidadeNegadaException(String codigoProduto, String segmento) {
        super("Product %s does not allow contracting for segment %s.".formatted(codigoProduto, segmento));
    }
}
```

**File:** `domain/exception/OperacaoNaoEncontradaException.java`

```java
package br.com.sicredi.creditservice.domain.exception;

import java.util.UUID;

public class OperacaoNaoEncontradaException extends RuntimeException {
    public OperacaoNaoEncontradaException(UUID id) {
        super("Credit operation not found: %s".formatted(id));
    }
}
```

**File:** `domain/exception/ProdutoServiceIndisponivelException.java`

```java
package br.com.sicredi.creditservice.domain.exception;

public class ProdutoServiceIndisponivelException extends RuntimeException {
    public ProdutoServiceIndisponivelException(Throwable cause) {
        super("Product eligibility service is unavailable.", cause);
    }
}
```

---

## Final package layout after Phase 1

```
br.com.sicredi.creditservice.domain
 model
   ├── Segmento.java
   ├── OperacaoCredito.java
   └── OperacaoSocio.java
 exception
    ├── AgroSemAreaException.java
    ├── ElegibilidadeNegadaException.java
    ├── OperacaoNaoEncontradaException.java
    └── ProdutoServiceIndisponivelException.java
```

---

## Validation checklist

Before moving to Phase 2, confirm:

- [ ] All classes compile with `mvn compile` — no errors
- [ ] No imports from `org.springframework`, `jakarta.persistence`, or `com.fasterxml`
- [ ] `OperacaoCredito` and `OperacaoSocio` are records (immutable, no setters)
- [ ] All four exceptions extend `RuntimeException` with a meaningful message
- [ ] `areaBeneficiadaHa` is `BigDecimal` (nullable) — no `Optional` wrapper at the model level
- [ ] `codigoConta` is `String`, not a numeric type
