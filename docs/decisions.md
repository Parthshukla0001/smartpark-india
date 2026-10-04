# Architecture Decision Records (ADRs) — SmartPark India

This document records the foundational architectural decisions made for the SmartPark India platform during Phase 1.

---

## ADR 001: PostgreSQL as Primary Database (over MongoDB)

### Status
Accepted

### Context
SmartPark India handles parking management, reservations, and inventory allocation across physical facilities. These domains require strict ACID compliance, transactional integrity to prevent double-booking, and relational constraints between facilities, floors, slots, users, and bookings.

### Decision
Use **PostgreSQL 16+** as the primary relational database.

### Consequences
- **Positive:**
  - Strong transactional guarantees (ACID) prevent race conditions and inventory corruption.
  - Native foreign key enforcement, multi-column unique constraints, and check constraints guarantee data validity at the storage layer.
  - Excellent support for geospatial operations (PostGIS can be added seamlessly in future phases without migrating databases).
  - Rich ecosystem and battle-tested Spring Data JPA integration.
- **Negative:**
  - Strict schema evolution requires disciplined migrations (addressed via Flyway).

---

## ADR 002: Flyway Migrations as Schema Source of Truth (over Hibernate auto-ddl)

### Status
Accepted

### Context
Using `spring.jpa.hibernate.ddl-auto=update` or `create-drop` in production causes unpredictable schema drift, silent failures, accidental table drops, and non-deterministic index creation.

### Decision
Use **Flyway** as the sole source of truth for database schema creation and evolution. Configure Hibernate with `spring.jpa.hibernate.ddl-auto=validate` to enforce that entity definitions strictly match the database schema during application startup.

### Consequences
- **Positive:**
  - Version-controlled SQL scripts (`V1__initial_schema.sql`) are deterministic, auditable, and reproducible across local, staging, and production environments.
  - Hibernate will abort startup immediately if an entity mapping diverges from the actual database schema.
- **Negative:**
  - Developers must write explicit SQL migration scripts alongside entity modifications.

---

## ADR 003: Single Role Assignment per User in Phase 1

### Status
Accepted

### Context
Users require role-based differentiation (DRIVER, OPERATOR, ADMIN). A full many-to-many role and permission system adds unnecessary join tables, junction entities, and authorization complexity at this stage.

### Decision
Implement a normalized `roles` table with a foreign key (`role_id`) in `users`, giving each user one primary role initially (`DRIVER`, `OPERATOR`, or `ADMIN`).

### Consequences
- **Positive:**
  - Clean, minimal, and performant joins.
  - Foreign key constraints ensure only valid roles can be assigned.
  - Can be gracefully migrated to a many-to-many `user_roles` table in a future phase if multi-role accounts become a validated requirement.
- **Negative:**
  - A user cannot simultaneously act as an Operator and a Driver under a single account in Phase 1.

---

## ADR 004: Surrogate `BIGINT GENERATED ALWAYS AS IDENTITY` Primary Keys

### Status
Accepted

### Context
We evaluated primary key generation strategies: Natural keys (e.g. email or registration number), UUIDv4, and 64-bit auto-incrementing integers (`BIGINT`).

### Decision
Standardize on standard SQL `BIGINT GENERATED ALWAYS AS IDENTITY` (mapped to `GenerationType.IDENTITY` in JPA).

### Consequences
- **Positive:**
  - Compact 8-byte keys minimize B-tree index sizes and cache footprint.
  - High insertion performance and predictable sequential disk locality in PostgreSQL.
  - Natural keys (email, registration number) can still be updated without cascading foreign key updates.
- **Negative:**
  - IDs are sequentially guessable if exposed directly through public URLs (will be mitigated in later API phases using public external IDs or obfuscation if needed).

---

## ADR 005: Schema Preparation for Double-Booking Prevention

### Status
Accepted

### Context
A parking slot must never have overlapping active bookings. Solving this purely through application-level `if` checks leads to race conditions under concurrent requests.

### Decision
In Phase 1, establish the foundational database primitives:
1. `chk_booking_time_validity` ensuring `end_time > start_time`.
2. Composite index `idx_bookings_slot_times` on `(parking_slot_id, start_time, end_time)`.
3. Dedicated booking lifecycle states (`PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `EXPIRED`).

Full concurrency handling (PostgreSQL exclusion constraints with `btree_gist`, pessimistic locking, or Redis distributed locks) will be implemented deliberately in the dedicated concurrency phase.

### Consequences
- **Positive:**
  - High-speed range queries for availability queries.
  - Avoids premature, complex concurrency code before the booking service architecture is ready.
- **Negative:**
  - Application logic must wait until Phase 2/3 for complete concurrency locking guarantees.
