# Architecture Documentation

## Overview

This project follows a **microservice architecture** where each service is independently deployable, has its own database schema, and communicates with other services via REST (OpenFeign) or events (Apache Kafka).

---

## Service Responsibilities

### Auth Service (port 8081)
- Issues and validates JWT tokens using HMAC-SHA512
- Manages user registration and login
- All other services validate JWT tokens using the same shared secret
- **DB Schema:** `auth` — tables: `users`

### Customer Service (port 8082)
- Full CRUD for customer management
- Validates JWT on every request via `JwtAuthFilter`
- Prevents duplicate email registration (409 Conflict)
- **DB Schema:** `customers` — tables: `customers`

### Product Service (port 8083)
- Full CRUD for product management
- Category-based organization
- Validates JWT on every request
- **DB Schema:** `products` — tables: `products`

### Stock Service (port 8084)
- Tracks inventory per product ID
- `increase`: creates stock record on first call, then adds quantity
- `decrease`: validates sufficient quantity before reducing (409 if insufficient)
- Both operations are `@Transactional`
- **DB Schema:** `stocks` — tables: `stocks`

### Order Service (port 8085)
- Creates orders with status lifecycle: CREATED → PAYMENT_PENDING → PAID → SHIPPED → CANCELLED
- On order creation:
  1. Saves order to DB
  2. Calls Stock Service via **OpenFeign** to decrease stock
  3. Publishes **OrderCreatedEvent** to Kafka topic `order-created`
- **DB Schema:** `orders` — tables: `orders`

### Notification Service (port 8086)
- Pure Kafka consumer — no database, no security layer
- Listens to `order-created` topic with group ID `notification-group`
- Simulates email notification via structured log output

---

## Communication Patterns

### Synchronous (REST via OpenFeign)
```
Order Service ──► Stock Service
  POST /stocks/decrease
  Authorization: Bearer {token} (forwarded via FeignClientConfig interceptor)
```

### Asynchronous (Apache Kafka)
```
Order Service ──► Kafka (order-created) ──► Notification Service
  Producer: JsonSerializer
  Consumer: JsonDeserializer, group-id: notification-group
```

---

## Security Architecture

All services except Notification Service implement JWT validation:

```
Request
  │
  ▼
JwtAuthFilter (OncePerRequestFilter)
  │
  ├── Extract "Authorization: Bearer {token}" header
  ├── Validate token signature using shared secret
  ├── Extract email + role from claims
  └── Set SecurityContext → request proceeds
```

Public endpoints (no token required):
- `POST /auth/register`
- `POST /auth/login`
- `/swagger-ui/**`
- `/v3/api-docs/**`

---

## Database Architecture

Single PostgreSQL instance with isolated schemas:

```
PostgreSQL (ecommerce database)
├── auth schema
│   └── users (id, email, password, role, created_at)
├── customers schema
│   └── customers (id, name, surname, email, phone, address, created_at)
├── products schema
│   └── products (id, name, description, price, category, created_at)
├── stocks schema
│   └── stocks (id, product_id, quantity, updated_at)
└── orders schema
    └── orders (id, customer_id, product_id, quantity, status, created_at)
```

All schema migrations are managed by **Liquibase** changesets. Changesets are immutable once applied.

---

## Docker Architecture

```
docker-compose
├── Infrastructure
│   ├── postgres:15          (port 5433)
│   ├── confluentinc/cp-zookeeper:7.5.0
│   └── confluentinc/cp-kafka:7.5.0  (port 9092)
└── Services
    ├── auth-service         (port 8081)
    ├── customer-service     (port 8082)
    ├── product-service      (port 8083)
    ├── stock-service        (port 8084)
    ├── order-service        (port 8085)
    └── notification-service (port 8086)
```

Service startup order enforced via `depends_on` with `healthcheck` on postgres.

---

## Environment Variables

Each service reads configuration from environment variables at runtime, with local defaults for development:

| Variable | Used By | Example |
|---|---|---|
| `SPRING_DATASOURCE_URL` | All DB services | `jdbc:postgresql://postgres:5432/ecommerce` |
| `SPRING_DATASOURCE_USERNAME` | All DB services | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | All DB services | `secret` |
| `JWT_SECRET` | All services | 64-char hex string |
| `JWT_EXPIRATION_MS` | Auth Service | `86400000` (24h) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Order, Notification | `kafka:9092` |
| `SERVICES_STOCK_URL` | Order Service | `http://stock-service:8084` |

---

## Design Decisions

**Why separate schemas instead of separate databases?**
Easier to manage with a single Docker container while still maintaining data isolation between services. In a production setup, each service would have its own database instance.

**Why OpenFeign instead of RestTemplate?**
Feign provides declarative HTTP clients with automatic error handling, cleaner code, and better integration with Spring Cloud ecosystem.

**Why Kafka for notifications?**
Decouples the Order Service from Notification Service. Order creation succeeds even if Notification Service is temporarily down — events are replayed when it comes back.

**Why JJWT instead of Spring OAuth2?**
Simpler implementation for a microservice learning project. All services share the same secret key and validate tokens independently without a central auth server.
