# Sicredi Technical Challenge

## Overview

Credit is a core part of Sicredi's business. You are responsible for building the backend for a **Credit Acquisition** service.

The acquisition process starts when a member (associado) expresses interest in taking credit. A business manager presents the best available options (simulation). Once a product is chosen, the credit is contracted — a credit note is saved to the database representing the transaction. After contract signing (formalization), the funds are released to the member's account.

**Stack:** Java with Spring Boot.

---

## API Requirements

### POST — Contract a credit operation

The frontend will call an endpoint with the following JSON payload:

| Field | Description | Availability |
|---|---|---|
| `idAssociado` | Natural number identifying the member in Sicredi's database | Always sent correctly |
| `valorOperacao` | Natural number representing the final amount to be deposited in the member's account | Always sent correctly |
| `segmento` | One of: `"PF"` (personal credit), `"PJ"` (business), `"AGRO"` (agricultural) | Always sent correctly |
| `codigoProdutoCredito` | 3-character alphanumeric code identifying the credit product | Always sent correctly |
| `codigoConta` | 10-digit numeric code for the checking account where funds will be deposited | Always sent correctly |
| `areaBeneficiadaHa` | Size of the rural area benefited by the credit operation, in hectares | Sent correctly when available |

**Expected response:** the `idOperacaoCredito` — the identifier of the created operation in the database.

### GET — Query a credit operation

A second endpoint must allow querying a credit operation by its `idOperacaoCredito`. The response must include all fields from the contract request plus the `idOperacaoCredito` and the contracting timestamp.

---

## Conventions

- Mandatory fields will always be provided — the frontend respects the contract.
- Input data is always valid (e.g., `idAssociado` always exists, `codigoConta` is always well-formed, `codigoProdutoCredito` always exists, `segmento` is never invalid). Input validation is out of scope for this task.

---

## Business Rules

1. **Persistence:** When a credit operation meets all conditions, a credit note must be saved in a database (POSTGRE SQL). All operation data must be stored in a credit notes table, following the database's naming conventions. In addition to the received fields, a system-generated unique identifier and the contracting timestamp must also be stored.

2. **Product eligibility check:** An operation can only be contracted if the credit product allows it for the given `segmento` and `valorOperacao`. To verify this, call the product service:

   ```
   GET https://desafio-credito-sicredi.wiremockapi.cloud/produtos-credito/{codigoProdutoCredito}/permite-contratacao?segmento={segmento}&valorFinanciado={valorOperacao}
   ```

   The response is always `200 OK` with a JSON body containing a boolean field `"permiteContratar"`.

   Known products and their rules:

   | `codigoProdutoCredito` | Allowed `segmento`(s) | Min Value | Max Value |
   |---|---|---|---|
   | `101A` | PF | 500 | 5,000 |
   | `202B` | PJ | 5,000 | 50,000 |
   | `303C` | AGRO | 2,000 | 15,000 |
   | `404D` | PF | 1,000 | 20,000 |
   | `505E` | PJ | 10,000 | 100,000 |
   | `606F` | PF, PJ | 2,000 | 30,000 |
   | `707G` | AGRO, PF | 1,500 | 12,000 |
   | `808H` | AGRO, PJ | 5,000 | 60,000 |
   | `903C` | AGRO | 1,000 | 10,000 |
   | `909I` | PF, PJ, AGRO | 1,000 | 50,000 |

   The product service may experience downtime or intermittency — handle errors as you see fit. All products sent by the frontend are known to the product service.

3. **AGRO validation:** Operations with `segmento = AGRO` can only be contracted when `areaBeneficiadaHa` is provided and greater than zero. Reject the operation otherwise.

4. **PJ additional record:** Operations with `segmento = PJ` must have an additional record in a separate table, representing the link between the operation and the beneficiary partner. This record must contain the system-generated operation identifier and the `idAssociado` who requested the credit.
