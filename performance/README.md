# SmartPark India — Performance & Load Testing Suite (Phase 14)

This directory contains the reproducible, production-grade performance benchmarking and load-testing suite for the **SmartPark India** backend. All tests are automated using [Grafana k6](https://k6.io/) running inside Docker containers connected to the Docker Compose internal network.

---

## 1. Test Environment & Hardware Baseline

- **Host Machine**: AMD Ryzen 7 8840HS (8 Physical Cores, 16 Logical Processors @ 3.30 GHz base / 5.10 GHz boost)
- **Host Memory**: 32 GB DDR5 RAM (~31.28 GB addressable)
- **Operating System**: Windows 11 Home Single Language (Build 10.0.26100)
- **Container Runtime**: Docker Desktop 29.8.1 (WSL2 Linux Kernel 5.15)
- **Target Topology**:
  - `smartpark-backend`: Eclipse Temurin OpenJDK 21 JRE, Spring Boot 3.4.1, embedded Tomcat, HikariCP pool (10 connections)
  - `smartpark-postgres`: PostgreSQL 16.15 (Alpine Linux)
  - `smartpark-redis`: Redis 7.4 (Alpine Linux)
  - `smartpark-kafka`: Apache Kafka 3.8.0 (KRaft mode)
- **Benchmarking Tool**: Grafana k6 v2.3.0 (`grafana/k6:latest`) executed via Docker bridge network (`smartpark-india_smartpark-net`)

---

## 2. Seeded Database Scale

To ensure realistic database execution plans, index depth (B-Tree and GiST), and memory footprints, the database was seeded prior to benchmarking with:

- **Users**: 145 users (100 driver accounts `driver1@smartpark.in`..`driver100@smartpark.in`, 10 facility operators, 1 platform admin)
- **Vehicles**: 211 verified vehicles registered across drivers (`OD02BM1001`..`OD02BM1200`)
- **Parking Lots**: 42 active commercial facilities across 5 Indian metros (Bhubaneswar, Bengaluru, Mumbai, Hyderabad, Pune) with realistic operating schedules (`06:00` - `23:00` IST)
- **Parking Floors**: 71 floors across the facilities
- **Parking Slots**: 1,511 slots categorized across `REGULAR`, `EV_CHARGING`, and `ACCESSIBLE` types
- **Historical Bookings**: 10,000 completed/cancelled/expired records + 502 active future bookings (total: 10,506 bookings)
- **Post-Benchmark Bookings**: 27,393 records after intensive load and concurrency testing

Database statistics were analyzed using PostgreSQL `ANALYZE` after seeding.

---

## 3. Directory Structure

```
performance/
├── k6/
│   ├── auth.js                        # Scenario 3: Driver authentication & JWT issuance ramp
│   ├── booking-concurrency.js         # Scenario 1: 50 VUs simultaneous race condition verification
│   ├── booking.js                     # Scenario 5: Full booking lifecycle (Query -> Create -> Cancel)
│   ├── cache-comparison.js            # Initial cache warm/cold script
│   ├── cache-hit-miss-benchmark.js    # Scenario 2: 2,000 reqs cache enabled vs disabled experiment
│   ├── mixed-workload.js              # Scenario 6: Production-like mixed traffic (10->25->50->100 VUs)
│   └── parking.js                     # Scenario 4: Parking discovery & slot availability
├── reports/
│   └── baseline-performance-report.md # Comprehensive metrics, analysis, and interview defense
├── results/
│   └── baseline/                      # Raw JSON exports, summaries, and Actuator metrics metadata
├── check-metrics.ps1                  # PowerShell script to sample live Actuator metrics
├── parse-results.ps1                  # PowerShell script to format raw k6 JSON into markdown/console tables
├── run-benchmarks.ps1                 # Full automated benchmark pipeline runner
├── run-cache-experiment.ps1           # Cache hit vs cache miss comparative test runner
└── seed-dataset.sql                   # Realistic scale SQL seed script
```

---

## 4. How to Reproduce Benchmarks

### Prerequisites
1. Docker and Docker Compose running.
2. The SmartPark containers must be active and healthy:
   ```powershell
   docker compose ps
   ```

### Step 1: Seed the Dataset (One-time)
```powershell
Get-Content performance/seed-dataset.sql | docker exec -i smartpark-postgres psql -U postgres -d smartpark
```

### Step 2: Run Full Benchmark Suite
```powershell
powershell -ExecutionPolicy Bypass -File performance/run-benchmarks.ps1
```
This executes:
1. **Warm-up phase (10s)**: Primes JVM JIT compiler, HikariCP connections, and Redis cache.
2. **Scenario 1: Concurrency Race**: 50 VUs race for identical slot and interval simultaneously.
3. **Scenario 2: Cache Comparison**: Baseline warm cache reads.
4. **Scenario 3: Authentication Load**: 10 -> 25 -> 50 VUs stress testing login and BCrypt.
5. **Scenario 4: Parking Discovery**: High-frequency search and slot availability browsing.
6. **Scenario 5: Booking Lifecycle**: User history query, slot booking creation, and booking cancellation.
7. **Scenario 6: Mixed Workload**: Staged multi-tier traffic profile (10 -> 25 -> 50 -> 100 VUs).

### Step 3: Run Cache Hit vs. Miss Experiment
```powershell
powershell -ExecutionPolicy Bypass -File performance/run-cache-experiment.ps1
```
This dynamically benchmarks 2,000 requests against `/api/parking-lots/:id` with `CACHE_ENABLED=true` (Redis hits) versus `CACHE_ENABLED=false` (PostgreSQL direct queries) and outputs comparative tables.

---

## 5. Summary of Baseline Results

| Benchmark Scenario | Requests | Throughput (RPS) | P50 Latency | P90 Latency | P95 Latency | P99 Latency | Failure Rate |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Parking Discovery** | 173,625 | **2,094.06 req/s** | 3.96 ms | 9.78 ms | 12.55 ms | 21.04 ms | 0.00% |
| **Mixed Workload (100 VUs)** | 81,789 | **922.37 req/s** | 7.01 ms | 29.01 ms | 45.32 ms | 108.40 ms | 0.00% |
| **Booking Lifecycle** | 30,925 | **482.32 req/s** | 7.76 ms | 22.30 ms | 29.77 ms | 56.12 ms | 0.00% |
| **Authentication (BCrypt)** | 6,333 | **105.43 req/s** | 96.89 ms | 209.77 ms | 221.78 ms | 289.40 ms | 0.00% |
| **Cache Hits (Redis)** | 2,003 | **1,172.12 req/s** | 13.93 ms | 20.26 ms | 23.27 ms | 35.10 ms | 0.00% |
| **Cache Misses (Postgres)** | 2,003 | **510.09 req/s** | 30.59 ms | 49.68 ms | 61.74 ms | 88.50 ms | 0.00% |

### Concurrency Race Condition Guarantee
- **50 Concurrent VUs** simultaneously racing for the identical slot and overlapping time window:
  - **HTTP 201 Created**: Exactly **1** request succeeded.
  - **HTTP 409 Conflict**: Exactly **49** requests rejected with expected business conflict.
  - **HTTP 500 / System Failures**: Exactly **0**.
  - **Database Overlapping Active Bookings**: Exactly **0** verified in PostgreSQL.
