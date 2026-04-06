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
