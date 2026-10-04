# 🅿️ SmartPark India

**Smart Parking Management Platform for India**

> Currently implemented: **Phase 0 — Project Foundation**

SmartPark India is a comprehensive smart parking management platform designed for the Indian market. It will provide real-time parking availability, booking and reservations, payment integration, and administrative dashboards for parking operators.

---

## 📋 Current Phase

| Phase | Status |
|-------|--------|
| **Phase 0 — Project Foundation** | ✅ Complete |
| Phase 1 — Core Entities & Auth | 🔜 Planned |
| Phase 2 — Booking & Payments | 🔜 Planned |
| Phase 3 — Real-time Features | 🔜 Planned |
| Phase 4 — Observability & Performance | 🔜 Planned |

---

## 🛠 Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 21, Spring Boot 3.4.x, Maven |
| **Database** | PostgreSQL 16 |
| **Frontend** | React, Vite, TypeScript |
| **Infrastructure** | Docker, Docker Compose |
| **Version Control** | Git |

---

## 📁 Project Structure

```
smartpark-india/
│
├── backend/                        # Spring Boot application
│   ├── src/main/java/com/smartpark/
│   │   ├── SmartParkApplication.java
│   │   ├── config/
│   │   ├── controller/             # REST controllers
│   │   │   └── HealthController.java
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── service/
│   │   └── util/
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── frontend/                       # React + Vite application
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                           # Documentation
├── .env.example                    # Example environment variables
├── .gitignore
├── docker-compose.yml              # Docker services (PostgreSQL)
└── README.md
```

---

## ✅ Prerequisites

Ensure the following are installed:

- **Java 21** or later
- **Node.js 18+** and npm
- **Docker** and **Docker Compose**
- **Git**

---

## 🐘 PostgreSQL Setup

### Option 1: Using Docker (Recommended)

```bash
docker compose up -d
```

This starts a PostgreSQL 16 container with:
- Database: `smartpark`
- Default user: `postgres`
- Default password: `postgres`
- Port: `5432`
- Persistent volume: `smartpark_pgdata`

### Option 2: Local PostgreSQL

If you have PostgreSQL installed locally:

1. Create a database:
   ```sql
   CREATE DATABASE smartpark;
   ```
2. Copy `.env.example` to `.env` and update with your credentials.

---

## 🐳 Docker Setup

Start all services:

```bash
docker compose up -d
```

Stop services:

```bash
docker compose down
```

Stop and remove volumes:

```bash
docker compose down -v
```

Check service health:

```bash
docker compose ps
```

---

## 🚀 Starting the Backend

1. Ensure PostgreSQL is running (via Docker or locally).

2. Set environment variables (or use defaults):
   ```bash
   # On PowerShell
   $env:DB_HOST="localhost"
   $env:DB_PORT="5432"
   $env:DB_NAME="smartpark"
   $env:DB_USERNAME="postgres"
   $env:DB_PASSWORD="postgres"
   ```

3. Start the backend:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   On Windows:
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

4. The backend runs at: **http://localhost:8080**

---

## 💻 Starting the Frontend

1. Install dependencies:
   ```bash
   cd frontend
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```

3. The frontend runs at: **http://localhost:5173**

---

## 🏥 Health Endpoint

### Custom Health Endpoint

```
GET http://localhost:8080/api/health
```

Response:
```json
{
  "status": "UP",
  "service": "smartpark-backend"
}
```

### Spring Boot Actuator

```
GET http://localhost:8080/actuator/health
```

Exposed actuator endpoints: `health`, `info`, `metrics`

---

## ⚠️ Current Limitations

Phase 0 is the project foundation only. The following are **not yet implemented**:

- User authentication / JWT / Spring Security
- Parking lot entities and database schema
- Booking and reservation system
- Payment integration
- Real-time availability (WebSockets)
- Redis caching
- Kafka event streaming
- Rate limiting
- Admin dashboard
- Driver application UI
- AI/ML features

---

## 🗺 Future Roadmap

| Phase | Features |
|-------|----------|
| **Phase 1** | Database schema, core entities, authentication, user management |
| **Phase 2** | Parking management, booking system, payment integration |
| **Phase 3** | Real-time availability (WebSockets), Redis caching, Kafka events |
| **Phase 4** | Rate limiting, monitoring, observability, performance testing |
| **Phase 5** | Driver app UI, admin dashboard, maps integration |
| **Phase 6** | Docker production setup, CI/CD, deployment |

---

## 📝 License

This project is proprietary. All rights reserved.
