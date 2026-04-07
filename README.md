# Credit Service Core

Backend monorepo for the Sicredi Credit Acquisition service.

## Requirements

- Docker & Docker Compose
- Java 21
- Maven

## Setup

Copy the environment file and fill in the values:

```bash
cp .env.example .env
```

## Docker Commands

Start all containers in the background:

```bash
docker compose up -d
```

Force rebuild and restart all containers:

```bash
docker compose up -d --build --force-recreate
```

## Dev / Debug (without Docker) Commands

Ensure the database is running (`docker compose up -d postgres`), then from the service directory:

```bash
cd backend/credit-service

./mvnw clean install -DskipTests

./mvnw spring-boot:run

./mvnw test
```

The API will be available at `http://localhost:8080`.

## API Requests

HTTP request files are in `./api-requests/`. Use [httpyac](https://httpyac.github.io/) via `npx` — no installation required.

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

### consultar — Query a credit operation (GET)

Set `@idOperacaoCredito` in `consultar.http` to a real ID returned by a contratar success request, then run:

```bash
npx httpyac send api-requests/consultar.http --all
```

