# Meant

**Everything meant for you.**

Meant is a personalized shopping platform that lets users search and buy products across multiple online stores from one place.

Instead of showing users everything, Meant shows only products that match their preferences, goals, and values. It is not another marketplace. It is a personalized shopping layer on top of the internet.

## Product

Users can define persistent shopping preferences, such as:

- only organic food
- no polyester
- cotton clothing only
- healthy products
- sustainable brands
- specific budget limits
- highly rated products only

These preferences are applied automatically across every search.

## User Experience

1. The user enters a shopping request, for example: "Find me a durable organic cotton hoodie under €100."
2. Meant searches relevant merchants and products.
3. Results are filtered and ranked based on the user's explicit preferences, product relevance, price, availability, reviews, and merchant quality.
4. Each result explains why it is recommended, such as matching material preferences, staying within budget, having strong durability reviews, or shipping to the user's location.
5. The user selects a product and is redirected to the merchant checkout.

## Merchant Discovery

Meant maintains an index of merchants that support UCP and Shopify Storefront MCP.

For each merchant, Meant stores:

- store description
- product categories
- target audience
- sustainability signals
- popular searches
- supported capabilities
- checkout endpoint

Merchant profiles are embedded for semantic search.

## Product Search

Search happens in two stages:

1. Find the most relevant merchants using semantic search over merchant profiles.
2. Search products live only across those merchants.

Product results are cached and gradually indexed to improve speed and quality over time.

## Reviews

Reviews are a key part of Meant.

Meant aggregates available ratings and review data and generates concise AI insights:

- common advantages
- common complaints
- product quality signals
- recurring issues
- fit with the user's preferences

## Ranking

The ranking pipeline:

1. Hard filters, such as excluding polyester or products above budget.
2. Semantic retrieval to find relevant merchants and products.
3. Reranking to rank the best candidates against the user query.
4. Personalization based on the user's long-term profile and goals.

## Monetization

Meant is free for users.

Revenue comes from merchant commissions when users complete purchases through Meant.

## MVP Scope

- merchant registry from public UCP merchants
- merchant profiling through Shopify Storefront MCP
- semantic merchant search
- live product search
- user preferences
- personalized filtering and ranking
- product detail with AI explanation
- aggregated ratings and basic review insights
- redirect to merchant checkout

## Repository

This repository is a monorepo with a Java Spring Boot backend and a TanStack Start frontend.

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
