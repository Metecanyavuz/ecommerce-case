# 🛒 E-Commerce Microservices

A production-ready e-commerce backend built with **Java 21**, **Spring Boot 3.3**, and a full microservice architecture. Services communicate via **REST (OpenFeign)** and **Apache Kafka** for event-driven workflows.

---

## 🏗️ Architecture Overview

```
                        ┌─────────────────────────────────────────┐
                        │           Client / Postman / UI         │
                        └────────────────┬────────────────────────┘
                                         │ HTTP
              ┌──────────────────────────▼──────────────────────────────┐
              │                    Auth Service :8081                    │
              │              Register · Login · JWT Token                │
              └──────────────────────────┬──────────────────────────────┘
                          JWT Token      │      (validated by all services)
         ┌────────────────┬──────────────┼──────────────┬───────────────┐
         │                │              │              │               │
         ▼                ▼              ▼              ▼               ▼
  Customer :8082   Product :8083   Stock :8084    Order :8085   Notification :8086
  CRUD             CRUD            increase /     creates       Kafka Consumer
  JWT validate     JWT validate    decrease       order    ──►  logs events
                                   JWT validate   ├─ Feign ──► Stock (decrease)
                                                  └─ Kafka ──► order-created topic
```

---

## 🧩 Services

| Service | Port | Description |
|---|---|---|
| **auth-service** | 8081 | User registration, login, JWT token generation |
| **customer-service** | 8082 | Customer CRUD operations |
| **product-service** | 8083 | Product CRUD operations |
| **stock-service** | 8084 | Stock tracking, increase/decrease with safety checks |
| **order-service** | 8085 | Order management, Feign + Kafka integration |
| **notification-service** | 8086 | Kafka consumer, email simulation via logs |

---

## 🛠️ Tech Stack

### Backend
- Java 21
- Spring Boot 3.3.4
- Spring Security + JWT (JJWT 0.12.3)
- Spring Data JPA + Hibernate 6
- Spring Validation
- Spring Cloud OpenFeign
- Spring Kafka

### Database & Messaging
- PostgreSQL 15 (separate schema per service)
- Apache Kafka 7.5.0 + Zookeeper
- Liquibase (schema migration)

### DevOps
- Docker + Docker Compose
- GitHub Actions (CI/CD)

### Documentation
- Springdoc OpenAPI 2.3.0 (Swagger UI per service)

---

## 🚀 Running Locally

### Prerequisites
- Java 21
- Docker Desktop
- Maven 3.9+

### 1. Clone the repository
```bash
git clone https://github.com/Metecanyavuz/ecommerce-microservices.git
cd ecommerce-microservices
```

### 2. Start infrastructure + all services
```bash
cd ecommerce-infra
docker-compose up --build -d
```

### 3. Verify all containers are running
```bash
docker ps
```

Expected containers:
```
ecommerce-postgres
ecommerce-zookeeper
ecommerce-kafka
ecommerce-auth
ecommerce-customer
ecommerce-product
ecommerce-stock
ecommerce-order
ecommerce-notification
```

---

## 📖 API Documentation (Swagger UI)

| Service | Swagger URL |
|---|---|
| Auth | http://localhost:8081/swagger-ui/index.html |
| Customer | http://localhost:8082/swagger-ui/index.html |
| Product | http://localhost:8083/swagger-ui/index.html |
| Stock | http://localhost:8084/swagger-ui/index.html |
| Order | http://localhost:8085/swagger-ui/index.html |

---

## 🔐 Authentication Flow

```bash
# 1. Register
POST http://localhost:8081/auth/register
{
  "email": "user@example.com",
  "password": "password123",
  "role": "CUSTOMER"
}

# 2. Login — copy the token from the response
POST http://localhost:8081/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}
# Response: { "token": "eyJhbGci..." }

# 3. Use the token in all subsequent requests
Authorization: Bearer eyJhbGci...
```

---

## 📦 Order Flow (End-to-End)

```bash
# 1. Add a product
POST http://localhost:8083/products
{ "name": "Laptop", "price": 999.99, "category": "Electronics" }

# 2. Add stock for the product
POST http://localhost:8084/stocks/increase
{ "productId": 1, "quantity": 100 }

# 3. Add a customer
POST http://localhost:8082/customers
{ "name": "John", "surname": "Doe", "email": "john@example.com" }

# 4. Create an order — triggers Kafka event + stock decrease
POST http://localhost:8085/orders
{ "customerId": 1, "productId": 1, "quantity": 5 }

# 5. Check notification logs
docker-compose logs notification-service
```

---

## 🗃️ Database Schema

Each service uses a separate PostgreSQL schema within the same database:

| Service | Schema | Tables |
|---|---|---|
| auth-service | `auth` | users |
| customer-service | `customers` | customers |
| product-service | `products` | products |
| stock-service | `stocks` | stocks |
| order-service | `orders` | orders |

Schema migrations are managed by **Liquibase** and run automatically on service startup.

---

## 🔄 Kafka Event Flow

```
Order Service
    │
    ├──► Kafka Topic: order-created
    │         │
    │         └──► Notification Service (consumer)
    │                   └── logs: "[EMAIL SIM] Order confirmed"
    │
    └──► Feign: POST /stocks/decrease ──► Stock Service
```

---

## 👤 Author

**Metecan Yavuz**
GitHub: [@Metecanyavuz](https://github.com/Metecanyavuz)
