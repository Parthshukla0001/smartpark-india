# 🅿️ SmartPark India

**India-Focused Smart Parking Management Platform**

> Currently implemented: **Phase 3 — Parking Management APIs**

SmartPark India is an enterprise-grade smart parking management platform designed specifically for the Indian market. It provides real-time parking availability, slot booking and reservations, payment integration, and administrative dashboards for parking operators and drivers.

---

## 📋 Project Phases & Status

| Phase | Description | Status |
|---|---|---|
| **Phase 0 — Project Foundation** | Monorepo structure, Spring Boot 4 / Java 21, Vite + React + TS, Docker, Health endpoints | ✅ Complete |
| **Phase 1 — Domain Model & Database Foundation** | PostgreSQL schema, Flyway migrations, Core JPA entities (User, Role, Vehicle, ParkingLot, ParkingFloor, ParkingSlot, Booking), Integrity constraints, Indexes, Persistence tests | ✅ Complete |
| **Phase 2 — Authentication & Security Foundation** | Spring Security, BCrypt hashing, JWT authentication (JJWT), Role-Based Access Control (RBAC), DTOs, Login brute-force protection, Security audit logging, 28 automated tests | ✅ Complete |
| **Phase 3 — Parking Management APIs** | REST APIs for ParkingLot, ParkingFloor, ParkingSlot; Hierarchy validation; Safe deactivation/soft-delete; Search, filter & pagination; 50 automated tests | ✅ Complete |
| **Phase 4 — Booking Engine & Concurrency** | Slot reservation workflow, Double-booking concurrency prevention, Temporal availability | 🔜 Next |
| **Phase 5 — Payments & Real-time Integrations** | UPI / Payment Gateway, WebSockets availability stream, Redis cache | 🔜 Planned |
| **Phase 6 — Event Streaming & Observability** | Apache Kafka events, Prometheus / Grafana metrics, Rate limiting, Distributed tracing | 🔜 Planned |

---

## 🛠 Technology Stack

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| **Language** | Java | 21 (LTS standard) | Core backend language |
| **Backend Framework** | Spring Boot | 4.1.1 | Application runtime & dependency injection |
| **Database** | PostgreSQL | 16 (Alpine) | Primary relational ACID database |
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
- **Java 21** or later (`java --version`)
- **Node.js 18+** and npm (`node --version`)
- **Docker & Docker Compose** (`docker compose version`)
- **Git**

### 2. Start PostgreSQL via Docker Compose
```powershell
docker compose up -d
```
> **Port Note:** Docker maps container port `5432` to host port `5433` by default to avoid conflict with any existing local PostgreSQL service running on `5432`.

Verify container health:
```powershell
docker compose ps
```

### 3. Run Backend & Tests
```powershell
cd backend

# Execute test suite (Flyway migration + Hibernate validation + Persistence assertions)
.\mvnw.cmd test

# Run application server
.\mvnw.cmd spring-boot:run
```
The backend starts at **http://localhost:8080**.

### 4. Run Frontend Development Server
```powershell
cd frontend
npm install
npm run dev
```
The frontend is available at **http://localhost:5173**.

---

## 🏥 Verification Endpoints

| Endpoint | Method | Expected Output | Purpose |
|---|---|---|---|
| `/api/health` | `GET` | `{"status":"UP","service":"smartpark-backend"}` | Custom API health probe |
| `/actuator/health` | `GET` | `{"status":"UP", "components":{"db":{"status":"UP"}}}` | Actuator health & PostgreSQL connection check |

---

## ⚠️ Current Phase 1 Boundaries

The following areas are intentionally reserved for upcoming phases:
- User authentication, JWT issuance, and Spring Security
- Real-time slot reservations, booking transitions, and concurrency locking
- Payment gateway integration (UPI, cards, net banking)
- Redis caching & Kafka event messaging
- WebSocket availability broadcasting
- Production Docker orchestration and CI/CD pipelines
