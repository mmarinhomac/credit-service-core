# Credit Service Core

Backend monorepo for the Sicredi Credit Acquisition service.

## Requirements

- Docker & Docker Compose
- Java 21
- Maven
- [httpyac](https://httpyac.github.io/) (for running API requests)

## Setup

Copy the environment file and fill in the values:

```bash
cp .env.example .env
```

## Running with Docker

Start all containers in the background:

```bash
docker compose up -d
```

Force rebuild and restart all containers:

```bash
docker compose up -d --build --force-recreate
```

## Dev / Debug (without Docker)

Ensure the database is running (`docker compose up -d postgres`), then from the service directory:

```bash
cd backend/credit-service

./mvnw clean install

./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

## API Requests

HTTP request files are in `./api-requests/`. Use [httpyac](https://httpyac.github.io/) to run them from the command line.

Install httpyac globally (once):

```bash
npm install -g httpyac
```

### contratar.http — Contract a credit operation (POST)

Run a specific request by its name (the comment after `###`):

```bash
# PF — eligible
httpyac send api-requests/contratar.http --name "[PF] Product 101A — eligible (value within 500–5000) → 201 Created"

# PJ — eligible (also persists operacao_socio record)
httpyac send api-requests/contratar.http --name "[PJ] Product 202B — eligible (value within 5000–50000) → 201 Created"

# AGRO — eligible, with area
httpyac send api-requests/contratar.http --name "[AGRO] Product 303C — eligible, with area → 201 Created"

# AGRO — missing area → 422
httpyac send api-requests/contratar.http --name "[AGRO] Missing areaBeneficiadaHa → 422 Unprocessable Entity"

# PF — value out of range → 422
httpyac send api-requests/contratar.http --name "[PF] Product 101A — value exceeds max (5000) → 422 Unprocessable Entity"
```

Run all requests in the file at once:

```bash
httpyac send api-requests/contratar.http --all
```

### consultar.http — Query a credit operation (GET)

Replace the `idOperacaoCredito` variable with a real ID returned by a POST above, then run:

```bash
# Fetch an existing operation → 200 OK
httpyac send api-requests/consultar.http --name "Fetch an existing operation → 200 OK"

# Non-existent ID → 404
httpyac send api-requests/consultar.http --name "Operation not found → 404 Not Found"
```

Or run all GET requests at once:

```bash
httpyac send api-requests/consultar.http --all
```

### Override the base URL

By default all requests target `http://localhost:8080`. Pass a different URL with `--var`:

```bash
httpyac send api-requests/contratar.http --all --var baseUrl=http://localhost:9090
```
