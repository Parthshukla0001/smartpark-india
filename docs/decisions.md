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

---

## ADR 006: Stateless JWT Authentication via Spring Security

### Status
Accepted

### Context
SmartPark India serves mobile and web clients across distributed services. Server-side session storage (HTTP sessions) requires session affinity/sticky sessions, increases server memory footprint, and creates horizontal scaling friction.

### Decision
Use **stateless JWT-based authentication** (via JJWT 0.12.x) integrated directly into Spring Security:
1. Session creation policy set to `SessionCreationPolicy.STATELESS`.
2. Public registration restricts default role assignment strictly to `DRIVER`.
3. JWT tokens contain non-sensitive claims (`userId`, `email`, `role`) signed using HMAC-SHA256.
4. Passwords hashed using standard `BCryptPasswordEncoder` (cost factor 10).
5. CSRF protection disabled strictly because authentication tokens are submitted via the `Authorization: Bearer <token>` header (not ambient browser cookies), which is immune to cross-site request forgery.

### Consequences
- **Positive:**
  - Eliminates server session state, enabling seamless horizontal scale-out.
  - Every API request is self-contained and independently verifiable.
  - Built-in role claim powers Spring Security's `@PreAuthorize("hasRole('...')")`.
- **Negative:**
  - Immediate token revocation prior to expiration requires a token blocklist (which will be implemented using Redis in a future phase).

---

## ADR 007: Baseline In-Memory Login Brute-Force Protection

### Status
Accepted

### Context
Exposing public login endpoints (`/api/auth/login`) invites credential stuffing and automated brute-force attacks. However, external infrastructure dependencies (such as Redis) are prohibited in Phase 2.

### Decision
Implement a **lightweight, thread-safe in-memory login attempt service** (`LoginAttemptService`):
1. Tracks consecutive failed attempts by normalized account email in a `ConcurrentHashMap`.
2. Threshold of 5 consecutive failures triggers an automatic 15-minute temporary lockout.
3. Successful authentication resets the failure counter immediately.
4. An automated cleanup routine trims expired entries to prevent unbounded memory consumption.

### Consequences
- **Positive:**
  - Protects against brute-force attacks in single-instance and development environments without third-party dependencies.
  - Testable entirely in isolation with unit and integration tests.
- **Negative:**
  - **Known limitation:** State is local to the running JVM instance and does not share rate-limiting data across multiple instances in a cluster. This temporary baseline will be upgraded to distributed Redis rate limiting in a subsequent phase.

---

## ADR 008: Parking Hierarchy Architecture, Authorization, and Soft-Deactivation

### Status
Accepted

### Context
Phase 3 requires REST APIs for managing the parking facility infrastructure: `ParkingLot` → `ParkingFloor` → `ParkingSlot`. We must prevent orphaned records, ensure strict multi-tenant facility boundaries, prevent destructive hard deletion that breaks future reservation histories, enforce secure RBAC, and handle database constraint race conditions cleanly.

### Decision
1. **Hierarchical REST Routing**:
   - APIs reflect physical relationships: `/api/parking-lots/{lotId}/floors/{floorId}/slots/{slotId}`.
   - Services perform full upward parent-ownership validation on all operations, rejecting URL mismatch fraud or cross-tenant parameter attacks (e.g. requesting slot 20 under floor 10 when slot 20 actually resides on floor 11).
2. **Strict RBAC Differentiation**:
   - `ADMIN`: Unrestricted infrastructure administration, soft-deactivating lots, deleting empty floors, and configuring slots.
   - `OPERATOR`: Permitted to create/update lots, floors, and slots, and toggle operational statuses (`AVAILABLE`, `OUT_OF_SERVICE`).
   - `DRIVER`: Strictly read-only access. Automatically restricted to active parking lots (`active = true`). All mutation attempts receive HTTP 403 Forbidden.
3. **Safe Deactivation / Soft-Delete Semantics**:
   - Deleting a `ParkingLot` sets `active = false` rather than executing SQL `DELETE`.
   - Deleting a `ParkingSlot` sets `status = OUT_OF_SERVICE`.
   - Deleting a `ParkingFloor` requires that the floor contains zero slots (`countByParkingFloorId == 0`). If slots exist, deletion is rejected with 400 Bad Request to protect historical foreign key integrity.
4. **DTO Encapsulation & Safe Pagination**:
   - Database entities are never exposed in REST controllers. All inputs and outputs use dedicated request/response DTOs.
   - Pagination uses `PageResponse<T>` with bounded page sizes (`max = 100`) and a strict whitelist of allowable sort properties to prevent SQL manipulation or denial-of-service.
5. **Declarative Transaction Boundaries**:
   - Write operations execute under `@Transactional` boundaries at the service layer.
   - Query operations execute under `@Transactional(readOnly = true)`.

### Consequences
- **Positive:**
  - Complete protection against relational hierarchy spoofing.
  - Zero accidental data destruction for future booking histories.
  - Clear separation between public customer browsing and facility staff operations.
- **Negative:**
  - Deactivating a parking lot requires subsequent booking queries to explicitly check the active flag (which is already enforced in the repository layer).

---

## ADR 009: Core Booking Engine, Interval Overlap Logic, and Concurrency Boundaries

### Status
Accepted

### Context
Phase 4 requires core reservation capabilities for drivers: creating bookings, retrieving personal booking histories, and canceling existing reservations. The engine must validate vehicle ownership, parking slot operational readiness, parent facility operating hours, temporal sanity, and ensure that overlapping reservations on the same slot are rejected. Crucially, the boundary between application-level checks and concurrent double-booking protection must be explicitly architected.

### Decision
1. **Server-Side Ownership & Security Principle**:
   - The booking owner is never accepted from the request payload. `CurrentUserService` resolves the authenticated principal directly from the validated JWT.
   - Vehicle ownership is verified: `vehicle.getUser().getId().equals(currentUser.getId())`. Mismatched vehicle IDs return 404 Not Found to prevent enumeration or cross-account misuse.
   - Inactive vehicles (`vehicle.isActive() == false`) are barred from creating bookings.
2. **Temporal & Facility Boundary Rules**:
   - Booking `startTime` must be strictly before `endTime`, cannot start in the past (validated against injectable `java.time.Clock`), and duration is bounded to [15 minutes, 24 hours].
   - If operating hours are defined on the parent `ParkingLot`, bookings must fall strictly within `openingTime` and `closingTime` (evaluated in India Standard Time `Asia/Kolkata`).
3. **Interval-Overlap Availability Query**:
   - Overlap is evaluated in PostgreSQL using index `idx_bookings_slot_times`:
     `b.parkingSlot.id = :slotId AND b.startTime < :requestedEndTime AND b.endTime > :requestedStartTime AND b.status IN ('CONFIRMED', 'PENDING')`
   - Active reserving statuses block booking (`CONFIRMED`, `PENDING`). Inactive statuses (`CANCELLED`, `COMPLETED`, `EXPIRED`) release slot availability.
   - Adjacent bookings (where one ends exactly when another begins) do not conflict.
4. **Booking Lifecycle & Status**:
   - Initial status is server-controlled and created as `CONFIRMED`.
   - Cancellation transitions `CONFIRMED` → `CANCELLED`.
   - Once `CANCELLED`, repeated cancellation attempts are rejected (400 Bad Request).
5. **Transaction Boundary & Concurrency Limitation**:
   - Creation and cancellation methods run inside atomic `@Transactional` service boundaries.
   - **Explicit Limitation**: Application-level availability checks (`SELECT` followed by `INSERT`) do **not** prevent concurrent race conditions. If two concurrent requests query availability at the exact same instant, both can pass the check before either transaction commits, resulting in a double-booking.
   - Solving concurrent double-booking is explicitly scheduled for Phase 5 (via database-level exclusion constraints, pessimistic locking, or distributed locks).



