# Phase 7 — Reorganize api-requests and Simplify README

## References

- [`copilot/architecture.md`](./architecture.md) — service endpoints and error contracts
- [`copilot/battle-plan.md`](./battle-plan.md) — segment rules (PF, PJ, AGRO) and eligibility scenarios
- [`copilot/challenge.md`](./challenge.md) — product table, business rules, and HTTP contract
- [`copilot/design.md`](./design.md) — project conventions and stack
- [`copilot/hurdles.md`](./hurdles.md) — do not commit; developer owns all version control actions

---

## Problem Statement

`api-requests/contratar.http` mixes all segments (PF, PJ, AGRO) and all outcomes (success, failure)
into a single file. This makes it impossible to run a focused set of scenarios with one command —
the user must always filter with `--name`, which is verbose and error-prone.

The README also requires a global `npm install -g httpyac`, which adds friction and pollutes the
global environment. `npx httpyac` achieves the same result with no prior installation.

---

## Target File Structure

```
api-requests/
├── contratar-PF-success.http      # PF happy paths → 201 Created
├── contratar-PF-failure.http      # PF rejections  → 422
├── contratar-PJ-success.http      # PJ happy paths → 201 Created
├── contratar-PJ-failure.http      # PJ rejections  → 422
├── contratar-AGRO-success.http    # AGRO happy paths → 201 Created
├── contratar-AGRO-failure.http    # AGRO rejections  → 422
└── consultar.http                 # GET by ID — unchanged except comment fix
```

`contratar.http` is deleted after the split is complete.

---

## Detailed Steps

### Step 1 — Create `contratar-PF-success.http`

New file. Contains all POST requests for segment `PF` that expect `201 Created`.
Requests to migrate from `contratar.http`:

| Request label | Source section |
|---|---|
| `[PF] Product 101A — eligible (value within 500–5000)` | PF section |
| `[PF] Product 404D — eligible (value within 1000–20000)` | PF section |
| `[PF] Product 606F — PF or PJ, value within 2000–30000` | Multi-segment section |
| `[PF] Product 909I — PF, PJ, or AGRO, value within 1000–50000` | Multi-segment section |

---

### Step 2 — Create `contratar-PF-failure.http`

New file. Contains all POST requests for segment `PF` that expect `422 Unprocessable Entity`.
Requests to migrate from `contratar.http`:

| Request label | Reason for rejection |
|---|---|
| `[PF] Product 101A — value exceeds max (5000)` | `permiteContratar: false` from product service |
| `[PF] Product 101A — value below min (500)` | `permiteContratar: false` from product service |

---

### Step 3 — Create `contratar-PJ-success.http`

New file. Contains all POST requests for segment `PJ` that expect `201 Created`.
All PJ operations also persist a record in `operacao_socio` — note this in the file header.
Requests to migrate from `contratar.http`:

| Request label | Source section |
|---|---|
| `[PJ] Product 202B — eligible (value within 5000–50000)` | PJ section |
| `[PJ] Product 505E — eligible (value within 10000–100000)` | PJ section |
| `[PJ] Product 606F — PF or PJ, value within 2000–30000` | Multi-segment section |

---

### Step 4 — Create `contratar-PJ-failure.http`

New file. Contains all POST requests for segment `PJ` that expect `422 Unprocessable Entity`.
Requests to migrate from `contratar.http`:

| Request label | Reason for rejection |
|---|---|
| `[PJ] Product 202B — value exceeds max (50000)` | `permiteContratar: false` from product service |

---

### Step 5 — Create `contratar-AGRO-success.http`

New file. Contains all POST requests for segment `AGRO` that expect `201 Created`.
All AGRO operations require `areaBeneficiadaHa > 0` — note this in the file header.
Requests to migrate from `contratar.http`:

| Request label | Source section |
|---|---|
| `[AGRO] Product 303C — eligible, with area` | AGRO section |
| `[AGRO] Product 903C — eligible, with area (value within 1000–10000)` | AGRO section |
| `[AGRO] Product 707G — AGRO or PF, value within 1500–12000` | Multi-segment section |

---

### Step 6 — Create `contratar-AGRO-failure.http`

New file. Contains all POST requests for segment `AGRO` that expect `422 Unprocessable Entity`.
Requests to migrate from `contratar.http`:

| Request label | Reason for rejection |
|---|---|
| `[AGRO] Missing areaBeneficiadaHa` | Rejected by use case — never reaches product service |
| `[AGRO] areaBeneficiadaHa = 0` | Rejected by use case — zero is explicitly invalid |

---

### Step 7 — Delete `contratar.http`

After all six files above are created and verified to contain all original requests, delete:

```
api-requests/contratar.http
```

---

### Step 8 — Update `consultar.http`

The only change needed is the header comment that references `contratar.http` by name.
Replace the line:

```
### Replace this variable with an idOperacaoCredito returned by a POST in contratar.http
```

With:

```
### Replace this variable with an idOperacaoCredito returned by a POST in contratar-*-success.http
```

No other changes to `consultar.http`.

---

### Step 9 — Update `README.md`

Four changes, in order:

**9a. Remove httpyac from Requirements**

Remove this line from the Requirements list:

```markdown
- [httpyac](https://httpyac.github.io/) (for running API requests)
```

**9b. Remove global install instruction**

Remove the "Install httpyac globally (once):" paragraph and its code block:

```bash
npm install -g httpyac
```

**9c. Rewrite the `contratar` subsection**

Replace the entire `### contratar.http — Contract a credit operation (POST)` subsection with one
command per file, no `--name` flags. The new subsection:

### contratar — Contract a credit operation (POST)

Run all requests in a file at once:

```bash
# PF — success
npx httpyac send api-requests/contratar-PF-success.http --all

# PF — failure (422)
npx httpyac send api-requests/contratar-PF-failure.http --all

# PJ — success (also creates operacao_socio record)
npx httpyac send api-requests/contratar-PJ-success.http --all

# PJ — failure (422)
npx httpyac send api-requests/contratar-PJ-failure.http --all

# AGRO — success
npx httpyac send api-requests/contratar-AGRO-success.http --all

# AGRO — failure (422)
npx httpyac send api-requests/contratar-AGRO-failure.http --all
```

**9d. Rewrite the `consultar` subsection**

Replace the entire `### consultar.http — Query a credit operation (GET)` subsection. Remove the
`--name` variants; keep a single `--all` command and the note about updating the variable:

### consultar — Query a credit operation (GET)

Set `@idOperacaoCredito` in `consultar.http` to a real ID returned by a contratar success
request, then run:

```bash
npx httpyac send api-requests/consultar.http --all
```

**9e. Update the Override section**

Replace the old `httpyac send` example with `npx httpyac send` and reference one of the new
file names:

### Override the base URL

By default all requests target `http://localhost:8080`. Pass a different URL with `--var`:

```bash
npx httpyac send api-requests/contratar-PF-success.http --all --var baseUrl=http://localhost:9090
```

---

## Verification

After all steps are done, confirm:

```bash
# No old file remains
ls api-requests/contratar.http 2>&1   # must say: No such file or directory

# All six new files exist
ls api-requests/contratar-*.http

# No old httpyac global install reference in README
grep "npm install -g httpyac" README.md   # must produce no output

# No --name flags left in README
grep "\-\-name" README.md                 # must produce no output
```
