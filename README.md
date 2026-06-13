# Meant

Meant is a monorepo with a Java Spring Boot backend and a TanStack Start frontend.

## Stack

- Backend: Java 25, Spring Boot 4.1.0, Maven, Hibernate via Spring Data JPA, Liquibase
- Database: PostgreSQL
- Frontend: TanStack Start, React, TypeScript, Bun

## Layout

```text
backend/   Spring Boot API
frontend/  TanStack Start app
```

## Local Development

Start PostgreSQL:

```sh
docker compose up -d postgres
```

Optional environment files:

```sh
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

Run the backend:

```sh
cd backend
mvn spring-boot:run
```

Run the frontend:

```sh
cd frontend
bun install
bun run dev
```

The backend listens on `http://localhost:8080`.
The frontend listens on `http://localhost:3000`.

Initial API endpoints:

- `GET /api/health`

## Useful Commands

```sh
cd backend && mvn test
cd frontend && bun run build
```
