# 🅿️ SmartPark India

**India-Focused Smart Parking Management Platform**

> Currently implemented: **Phase 13 — Docker & Production-Style Containerization**

SmartPark India is an enterprise-grade smart parking management platform designed specifically for the Indian market. It provides real-time parking availability, slot booking and reservations, payment integration, and administrative dashboards for parking operators and drivers.

---

## 📋 Project Phases & Status

| Phase | Description | Status |
|---|---|---|
| **Phase 0 — Project Foundation** | Monorepo structure, Spring Boot 4 / Java 21, Vite + React + TS, Docker, Health endpoints | ✅ Complete |
| **Phase 1 — Domain Model & Database Foundation** | PostgreSQL schema, Flyway migrations, Core JPA entities (User, Role, Vehicle, ParkingLot, ParkingFloor, ParkingSlot, Booking), Integrity constraints, Indexes, Persistence tests | ✅ Complete |
| **Phase 2 — Authentication & Security Foundation** | Spring Security, BCrypt hashing, JWT authentication (JJWT), Role-Based Access Control (RBAC), DTOs, Login brute-force protection, Security audit logging, 28 automated tests | ✅ Complete |
| **Phase 3 — Parking Management APIs** | REST APIs for ParkingLot, ParkingFloor, ParkingSlot; Hierarchy validation; Safe deactivation/soft-delete; Search, filter & pagination; 50 automated tests | ✅ Complete |
| **Phase 4 — Core Booking Engine** | Booking lifecycle (create, get, cancel), Vehicle ownership, Slot status & facility checks, Interval-overlap availability, Duration & operating hours validation; 71 automated tests | ✅ Complete |
| **Phase 5 — Concurrency & Double-Booking Prevention** | PostgreSQL `btree_gist` exclusion constraints, Pessimistic slot row locking (`PESSIMISTIC_WRITE`), Deadlock elimination, Multi-threaded race condition tests; 78 automated tests | ✅ Complete |
| **Phase 6 — Booking Lifecycle Management** | State machine (PENDING, CONFIRMED, CANCELLED, COMPLETED, EXPIRED), Driver cutoff deadline, Scheduled idempotent expiry, Pessimistic row locking; 99 automated tests | ✅ Complete |
| **Phase 7 — Redis Caching & Distributed Rate Limiting** | Redis 7 integration, Cache-aside for lots/slots, Post-commit invalidation, Atomic Lua fixed-window rate limiter, HTTP 429 Retry-After, Fail-open resilience; 110 automated tests | ✅ Complete |
| **Phase 8 — Real-Time Parking Updates (WebSockets)** | Spring WebSocket + STOMP, `/ws` endpoint, `/topic/parking-lots/{lotId}`, JWT STOMP auth, Post-commit event dispatch; 121 automated tests | ✅ Complete |
| **Phase 9 — Asynchronous Event Processing (Kafka & Outbox)** | Apache Kafka 3.8 (KRaft), Transactional Outbox Pattern, `FOR UPDATE SKIP LOCKED` poller, Idempotent consumer (`processed_events`), Retry & Dead Letter Topic (`DLT`), Notification dispatch; 129 automated tests | ✅ Complete |
| **Phase 10 — Query Optimization & Performance Analysis** | EXPLAIN ANALYZE measurement, N+1 query elimination via `@EntityGraph`, Composite & functional indexes (`V5`), In-memory sort removal, Paginated notification querying; 136 automated tests | ✅ Complete |
| **Phase 11 — Observability, Metrics, Health & Logging** | Spring Boot Actuator, Micrometer custom business metrics, Zero high-cardinality labels, Critical vs. Non-critical health checks (Postgres/Redis/Kafka), Correlation ID filter (`X-Correlation-ID`) & MDC tracing, Sanitized structured logging; 150 automated tests | ✅ Complete |
| **Phase 12 — Premium Frontend Completion** | Commercial-grade React + Vite + TS web application, 3 genuine role experiences (Driver, Operator, Admin), live STOMP WebSocket slot grid telemetry, Actuator metrics & health UI; 150 automated backend tests | ✅ Complete |
| **Phase 13 — Docker & Containerization** | Multi-stage Dockerfiles (Java 21 Temurin non-root user, Vite + Nginx Alpine reverse proxy), Docker Compose multi-service architecture (Postgres, Redis, Kafka, Backend, Frontend), health checks, persistent volumes, container-to-container internal networking; 158 automated tests | ✅ Complete |
| **Phase 14 — Payments & Integrations** | UPI / Payment Gateway integration, Webhooks, Reconciliation | 🔜 Planned |

---

## 🛠 Technology Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Language** | Java | 21 (LTS standard) | Core backend language |
| **Backend Framework** | Spring Boot | 4.1.1 | Application runtime & dependency injection |
| **Database** | PostgreSQL | 16 (Alpine) | Primary relational ACID database |
| **Event Streaming** | Apache Kafka | 3.8.0 (KRaft) | Asynchronous backend event streaming & DLT |
| **Cache & Rate Limiter** | Redis | 7.x (Alpine) | Cache-aside & distributed rate limiting |
| **Real-Time WebSockets** | Spring WebSocket + STOMP | 4.x | Real-time browser updates |
| **Migration Tool** | Flyway | 12.x | Schema versioning & migration truth |
| **ORM / Data Access** | Spring Data JPA / Hibernate | 7.x | Persistence layer & entity lifecycle |
| **Frontend** | React + Vite | 19.x / 8.x | Web client application |
| **Containers** | Docker & Docker Compose | Compose v2 | Local containerized infrastructure |
| **Build Tools** | Maven Wrapper (`mvnw`), npm | 3.9.x / 11.x | Reproducible builds |

---

## 🏛 Core Domain Entities & Database Schema

The Phase 1 schema defines 7 core relational tables managed by Flyway (`V1__initial_schema.sql`):

1. **`roles`**: System roles (`DRIVER`, `OPERATOR`, `ADMIN`).
2. **`users`**: Platform users with email/phone uniqueness, password hash storage, and role assignment.
3. **`vehicles`**: Vehicles associated with users, supporting Indian registration plates (e.g., `MH12AB1234`) and vehicle types (`CAR`, `MOTORCYCLE`, `SUV`, `OTHER`).
4. **`parking_lots`**: Physical parking facilities with GPS coordinates (`latitude`, `longitude`), city/state, PIN code, and operating hours.
5. **`parking_floors`**: Facility levels/floors with per-lot unique floor numbers.
6. **`parking_slots`**: Individual parking bays with per-floor unique slot numbers, slot types (`REGULAR`, `DISABLED`, `EV`), and operational status (`AVAILABLE`, `OUT_OF_SERVICE`).
7. **`bookings`**: Parking slot reservations referencing User, Vehicle, and Slot with temporal validation (`end_time > start_time`) and status lifecycle (`PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `EXPIRED`).

Detailed database documentation is available in:
- [Database Design & Schema Specification](docs/database-design.md)
- [Architecture Decision Records (ADRs)](docs/decisions.md)

---

## 📁 Monorepo Structure

```
smartpark-india/
├── backend/                                   # Spring Boot application
│   ├── .mvn/wrapper/                          # Maven wrapper binaries
│   ├── src/main/java/com/smartpark/
│   │   ├── SmartParkApplication.java          # Spring Boot main class
│   │   ├── config/                            # Configuration classes
│   │   ├── controller/                        # REST API controllers
│   │   │   └── HealthController.java          # GET /api/health
│   │   ├── dto/                               # Data Transfer Objects
│   │   ├── entity/                            # JPA entities & enums
│   │   │   ├── Booking.java
│   │   │   ├── BookingStatus.java
│   │   │   ├── ParkingFloor.java
│   │   │   ├── ParkingLot.java
│   │   │   ├── ParkingSlot.java
│   │   │   ├── Role.java
│   │   │   ├── RoleType.java
│   │   │   ├── SlotStatus.java
│   │   │   ├── SlotType.java
│   │   │   ├── User.java
│   │   │   ├── Vehicle.java
│   │   │   └── VehicleType.java
│   │   ├── exception/                         # Domain exception handlers
│   │   ├── repository/                        # Spring Data JPA repositories
│   │   │   ├── BookingRepository.java
│   │   │   ├── ParkingFloorRepository.java
│   │   │   ├── ParkingLotRepository.java
│   │   │   ├── ParkingSlotRepository.java
│   │   │   ├── RoleRepository.java
│   │   │   ├── UserRepository.java
│   │   │   └── VehicleRepository.java
│   │   ├── service/                           # Business logic services
│   │   └── util/                              # Utilities
│   ├── src/main/resources/
│   │   ├── application.yml                    # App configuration
│   │   └── db/migration/
│   │       └── V1__initial_schema.sql         # Flyway initial schema migration
│   ├── src/test/java/com/smartpark/
│   │   ├── SmartParkApplicationTests.java     # Startup & context tests
│   │   └── DomainPersistenceTests.java        # Entity, relationship & constraint tests
│   ├── mvnw / mvnw.cmd                        # Maven wrapper scripts
│   └── pom.xml                                # Maven project specification
├── frontend/                                  # React + Vite TypeScript client
│   ├── src/
│   │   ├── App.tsx                            # Landing component
│   │   ├── App.css
│   │   ├── index.css
│   │   └── main.tsx
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── docs/                                      # Project architecture docs
│   ├── database-design.md                     # Tables, constraints, and indexes
│   └── decisions.md                           # Architecture Decision Records (ADRs)
├── .env.example                               # Sample environment variables
├── .gitignore
├── docker-compose.yml                         # PostgreSQL 16 container definition
└── README.md
```

---

## 🚀 Getting Started

### 1. Prerequisites
- **Docker & Docker Compose** (`docker compose version` — Docker Desktop or Engine 24+)
- **Java 21** (Temurin LTS standard) — *for local host testing and development*
- **Node.js 20+** & npm — *for local frontend development*
- **Git**

---

### 2. Quick Start: Full Containerized Stack (Recommended)

To run the complete SmartPark India platform in Docker:

1. **Configure Environment:**
   Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. **Build & Start All Services:**
   ```bash
   docker compose up --build -d
   ```

3. **Check Container Status & Health:**
   ```bash
   docker compose ps
   ```
   All 5 services should transition to `(healthy)`:
   - `smartpark-postgres`: PostgreSQL 16 on port `5433` (internal `5432`)
   - `smartpark-redis`: Redis 7 on port `6379` (internal `6379`)
   - `smartpark-kafka`: Apache Kafka 3.8 on port `9092` (internal `9092`)
   - `smartpark-backend`: Spring Boot 4 / Java 21 on port `8080` (internal `8080`)
   - `smartpark-frontend`: Nginx Alpine reverse proxy on ports `5173` & `3000`

4. **Access the Applications:**
   - **Web Application**: [http://localhost:5173](http://localhost:5173) (or [http://localhost:3000](http://localhost:3000))
   - **Backend API Direct**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
   - **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - **WebSocket STOMP Endpoint**: `ws://localhost:5173/ws` (proxied by Nginx)

5. **View Logs:**
   ```bash
   # Follow backend logs
   docker compose logs -f backend

   # Follow all container logs
   docker compose logs -f
   ```

6. **Stop the Application:**
   ```bash
   # Stop containers (preserves database & redis volumes)
   docker compose down

   # Stop and wipe all persistent volumes
   docker compose down -v
   ```

---

### 3. Local Development Mode

If running backend or frontend locally on the host machine:

1. **Start only infrastructure services in Docker:**
   ```bash
   docker compose up -d postgres redis kafka
   ```

2. **Run Backend Tests & Application:**
   ```bash
   cd backend

   # Run complete automated test suite (158 tests)
   .\mvnw.cmd test

   # Run application server
   .\mvnw.cmd spring-boot:run
   ```

3. **Run Frontend Development Server:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

---

## 🏗 Container Architecture & Networking

```
Host Browser / Client
   │
   ├── [Port 5173 / 3000] ──► smartpark-frontend (Nginx Alpine)
   │                             ├── /          ──► Vite SPA Static Assets
   │                             ├── /api/      ──► http://backend:8080/api/
   │                             ├── /actuator/ ──► http://backend:8080/actuator/
   │                             └── /ws        ──► http://backend:8080/ws (WebSocket Upgrade)
   │
   └── [Port 8080]        ──► smartpark-backend (Java 21 JRE, User: smartpark)
                                 ├── jdbc:postgresql://postgres:5432/smartpark
                                 ├── redis://redis:6379
                                 └── kafka:9092 (INTERNAL listener)
```

- **Isolated Docker Network (`smartpark-net`)**: Container-to-container traffic uses service DNS names (`postgres`, `redis`, `kafka`, `backend`).
- **Kafka Multi-Listener Configuration**:
  - `INTERNAL://kafka:9092`: Used by `smartpark-backend` inside Docker network.
  - `EXTERNAL://localhost:9092`: Published on container port `9094` mapped to host `9092` for host-side integration tests.
- **Security & Least Privilege**:
  - Backend runs as unprivileged user `smartpark` (UID 10001).
  - Multi-stage builds produce minimal production images without Maven, Node, or build-time caches.

---

## 🏥 Observability & Verification Endpoints

| Endpoint | Method | Auth / Role | Expected Output | Purpose |
|---|---|---|---|---|
| `/api/health` | `GET` | Public | `{"status":"UP","service":"smartpark-backend"}` | High-level API health probe (echoes `X-Correlation-ID`) |
| `/actuator/health` | `GET` | Public | `{"status":"UP"}` | Sanitized Actuator health probe (no internal topology leaked) |
| `/actuator/health` | `GET` | `ROLE_ADMIN` | `{"status":"UP","components":{"db":...,"redis":...,"kafka":...}}` | Full health breakdown across critical & non-critical dependencies |
| `/actuator/metrics` | `GET` | `ROLE_ADMIN` | `{"names":["smartpark.bookings.created",...]}` | Micrometer metrics catalogue |
| `/actuator/metrics/{metricName}` | `GET` | `ROLE_ADMIN` | `{"name":"smartpark.bookings.created","measurements":[...]}` | Specific metric value inspection |

---

## ⚠️ Current Phase 13 Boundaries

The following areas are intentionally reserved for upcoming phases:
- Payment gateway integration (UPI, cards, net banking, webhooks)
- External monitoring visualization platform (Prometheus server, Grafana dashboards)
- Production cloud orchestration (Kubernetes manifests, Helm charts, cloud CI/CD pipelines)


