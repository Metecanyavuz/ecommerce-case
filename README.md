# 🛒 E-Commerce Microservices

A production-ready e-commerce backend built with **Java 21**, **Spring Boot 3.3**, and a full microservice architecture. Services communicate via **REST (OpenFeign)** and **Apache Kafka** for event-driven workflows.

> 🚀 **Live on Railway** — All services deployed and running.

---

## 🌐 Live URLs

| Service | URL |
|---|---|
| **Auth Service** | https://auth-service-production-c384.up.railway.app |
| **Customer Service** | https://customer-service-production-0c1a.up.railway.app |
| **Product Service** | https://product-service-production-d94d.up.railway.app |
| **Stock Service** | https://stock-service-production-40a8.up.railway.app |
| **Order Service** | https://order-service-production-8b5d.up.railway.app |
| **Admin Panel** | https://admin-service-production-99c2.up.railway.app/admin/login |

### 📖 Swagger UI
| Service | Swagger |
|---|---|
| Auth | https://auth-service-production-c384.up.railway.app/swagger-ui/index.html |
| Customer | https://customer-service-production-0c1a.up.railway.app/swagger-ui/index.html |
| Product | https://product-service-production-d94d.up.railway.app/swagger-ui/index.html |
| Stock | https://stock-service-production-40a8.up.railway.app/swagger-ui/index.html |
| Order | https://order-service-production-8b5d.up.railway.app/swagger-ui/index.html |

---

## 🏗️ Architecture Overview

```
                     ┌──────────────────────────────────────┐
                     │       Client / Postman / Admin UI     │
                     └───────────────┬──────────────────────┘
                                     │ HTTP + JWT
             ┌───────────────────────▼─────────────────────────┐
             │                  Auth Service :8081              │
             │           Register · Login · JWT Token           │
             └───────────────────────┬─────────────────────────┘
                       JWT Token     │     (validated by all services)
        ┌──────────────┬─────────────┼─────────────┬──────────────┐
        ▼              ▼             ▼             ▼              ▼
 Customer :8082  Product :8083  Stock :8084   Order :8085  Notification :8086
 CRUD            CRUD           increase/     creates      Kafka Consumer
 JWT validate    JWT validate   decrease      order   ──►  logs events
                                JWT validate  ├─ Feign ──► Stock (decrease)
                                              └─ Kafka ──► order-created topic

                     ┌──────────────────────────────────────┐
                     │          Admin Service :8090          │
                     │   Thymeleaf · Dashboard · Full CRUD   │
                     └──────────────────────────────────────┘
```

---

## 🧩 Services

| Service | Port | Description |
|---|---|---|
| **auth-service** | 8081 | User registration, login, JWT token generation |
| **customer-service** | 8082 | Customer CRUD operations |
| **product-service** | 8083 | Product CRUD operations |
| **stock-service** | 8084 | Stock tracking, increase/decrease with safety checks |
| **order-service** | 8085 | Order management — OpenFeign + Kafka producer |
| **notification-service** | 8086 | Kafka consumer, email simulation via logs |
| **admin-service** | 8090 | Thymeleaf admin panel — dashboard + full CRUD |

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
- Apache Kafka + Zookeeper
- Liquibase (automatic schema migration)

### Frontend
- Thymeleaf + Bootstrap 5 + Bootstrap Icons (Admin Panel)

### DevOps
- Docker + Docker Compose (local)
- Railway (cloud deployment)
- GitHub

### Testing
- JUnit 5
- Mockito
- Spring Boot Test + MockMvc
- H2 (in-memory DB for integration tests)

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
git clone https://github.com/Metecanyavuz/ecommerce-case.git
cd ecommerce-case
```

### 2. Start all services with Docker Compose
```bash
cd ecommerce-infra
docker-compose up --build -d
```

### 3. Verify containers are running
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
ecommerce-admin
```

### 4. Admin Panel (local)
```
URL:      http://localhost:8090/admin/login
Username: admin
Password: admin123
```

---

## 🔐 Authentication Flow

```bash
# 1. Register
POST /auth/register
{
  "email": "user@example.com",
  "password": "password123",
  "role": "CUSTOMER"
}

# 2. Login — copy the token from response
POST /auth/login
{
  "email": "user@example.com",
  "password": "password123"
}
# Response: { "token": "eyJhbGci...", "email": "...", "role": "CUSTOMER" }

# 3. Use token in all subsequent requests
Authorization: Bearer eyJhbGci...
```

---

## 📦 End-to-End Order Flow

```bash
# 1. Add a product
POST /products
{ "name": "Laptop", "price": 999.99, "category": "Electronics" }

# 2. Add stock
POST /stocks/increase
{ "productId": 1, "quantity": 100 }

# 3. Add a customer
POST /customers
{ "name": "John", "surname": "Doe", "email": "john@example.com" }

# 4. Create an order — triggers stock decrease + Kafka event
POST /orders
{ "customerId": 1, "productId": 1, "quantity": 5 }

# 5. Notification Service logs the event automatically:
# [NOTIFICATION] New order arrived!
# [EMAIL SIM] Verification mail sent to customer #1
```

---

## 🗃️ Database Schema

Each service uses a separate PostgreSQL schema — schemas are created automatically by Liquibase on startup.

| Service | Schema | Main Tables |
|---|---|---|
| auth-service | `auth` | users |
| customer-service | `customers` | customers |
| product-service | `products` | products |
| stock-service | `stocks` | stocks |
| order-service | `orders` | orders |

---

## 🔄 Kafka Event Flow

```
Order Service
    │
    ├──► Feign: POST /stocks/decrease ──► Stock Service
    │
    └──► Kafka Topic: order-created
              │
              └──► Notification Service
                        └── [EMAIL SIM] Order confirmed
```

---

## 🧪 Tests

```bash
# Product Service (14 tests)
cd product-service && mvn test

# Order Service (9 tests)
cd order-service && mvn test
```

| Test Class | Type | Tests |
|---|---|---|
| ProductServiceTest | Unit | 7 ✅ |
| ProductControllerTest | Controller | 3 ✅ |
| ProductIntegrationTest | Integration | 3 ✅ |
| OrderServiceTest | Unit | 5 ✅ |
| OrderIntegrationTest | Integration | 3 ✅ |
| **Total** | | **23 ✅** |

---

## 👤 Author

**Metecan Yavuz**
GitHub: [@Metecanyavuz](https://github.com/Metecanyavuz)
