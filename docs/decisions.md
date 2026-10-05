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

---

## ADR 010: Database-Level Booking Concurrency Protection and Exclusion Constraints

### Status
Accepted

### Context
In Phase 4, the core booking engine relied on an application-level availability check (`SELECT` overlap query followed by `INSERT`). Under concurrent traffic, two transactions ($T_1$ and $T_2$) could both execute the availability check at the exact same instant, find no conflicting row, and proceed to insert overlapping bookings for the same parking slot.

Phase 5 mandates a robust, production-grade concurrency control mechanism strictly within PostgreSQL and Spring Boot, without relying on Redis or distributed locking.

### Decision
1. **PostgreSQL GiST Partial Exclusion Constraint (`uq_bookings_no_overlapping_active_slots`)**:
   - Enabled the `btree_gist` extension in Flyway migration `V2__booking_concurrency_protection.sql`.
   - Added a partial exclusion constraint on table `bookings`:
     ```sql
     ALTER TABLE bookings
     ADD CONSTRAINT uq_bookings_no_overlapping_active_slots
     EXCLUDE USING gist (
         parking_slot_id WITH =,
         tstzrange(start_time, end_time, '[)') WITH &&
     )
     WHERE (status IN ('CONFIRMED', 'PENDING'));
     ```
   - **Temporal Semantics**: Used half-open intervals `[start_time, end_time)` (`'[)'`), ensuring that adjacent bookings (e.g. 10:00–11:00 and 11:00–12:00) touch at boundary timestamps without conflicting.
   - **Lifecycle Sensitivity**: Filtered with `WHERE (status IN ('CONFIRMED', 'PENDING'))` so that cancelled, completed, or expired bookings immediately release the slot interval for subsequent drivers without violating constraints.

2. **Pessimistic Slot Row-Level Locking (`PESSIMISTIC_WRITE`) for Serialized Admission**:
   - Concurrent uncoordinated `INSERT` statements colliding solely on a GiST exclusion constraint can trigger PostgreSQL transaction wait deadlocks (`40P01`).
   - To eliminate deadlocks and provide clean serialized admission, `BookingService.createBooking` queries the target parking slot using pessimistic write locking:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT s FROM ParkingSlot s WHERE s.id = :id")
     Optional<ParkingSlot> findByIdWithLock(@Param("id") Long id);
     ```
   - When $T_1$ and $T_2$ target the same slot simultaneously:
     - $T_1$ locks the slot row (`FOR UPDATE`), verifies availability, inserts booking, and commits.
     - $T_2$ waits on the slot row lock. Upon acquisition, $T_2$ executes the overlap check, immediately sees $T_1$'s committed booking, and throws `BookingConflictException` (HTTP 409).
   - Concurrent bookings targeting *different* slots acquire locks on separate rows and proceed completely in parallel without contention.

3. **Defense-in-Depth Exception Mapping**:
   - `GlobalExceptionHandler` catches:
     - `BookingConflictException` → HTTP 409 `BOOKING_CONFLICT`
     - `DataIntegrityViolationException` (matching `uq_bookings_no_overlapping_active_slots`) → HTTP 409 `BOOKING_CONFLICT`
     - `CannotAcquireLockException` and `PessimisticLockingFailureException` → HTTP 409 `BOOKING_CONFLICT`

4. **Why Alternatives Were Rejected**:
   - **In-Memory Java Locks (`synchronized` / `ReentrantLock`)**: Ineffective across multi-node or clustered deployments and lost on application restarts.
   - **JPA `@Version` Optimistic Locking on ParkingSlot**: Forces a version bump on `ParkingSlot` for every booking, causing false-positive optimistic lock exceptions even when booking disjoint, non-overlapping time intervals on the same day.
   - **Serializable Isolation Level**: Severe performance penalty, high serialization failure rates (`40001`), and complex retry requirements for unrelated transactions.
   - **Redis Distributed Locks**: Out of scope for Phase 5; introduces external infrastructure dependencies and network partition edge cases before relational guarantees are hardened.

### Consequences
- **Positive:**
  - Absolute mathematical and transactional guarantee against double bookings at the database level.
  - Zero deadlocks under concurrent load.
  - Non-overlapping adjacent bookings and concurrent multi-slot bookings execute seamlessly.
  - Uniform HTTP 409 error responses for conflict scenarios.
- **Negative:**
  - Serializes concurrent booking attempts on the exact same parking slot for the duration of the short transaction (~5-10ms).

---

## ADR 011: Booking Lifecycle State Machine, Cancellation Rules, and Automated Expiry

### Status
Accepted

### Context
Phase 6 requires robust booking lifecycle management across the defined lifecycle states: `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, and `EXPIRED`. We must prevent arbitrary client status modification, enforce strict role-based access control, enforce configurable driver cancellation cutoff deadlines, ensure automated idempotent expiry of elapsed reservations, and guarantee thread-safe concurrent lifecycle transitions using database transaction semantics.

### Decision
1. **Strict Finite State Machine (FSM)**:
   - **Active States**: `PENDING`, `CONFIRMED`.
   - **Terminal States**: `CANCELLED`, `COMPLETED`, `EXPIRED`.
   - **Legal Transitions**:
     - `PENDING -> CONFIRMED` (via authorized `POST /api/bookings/{id}/confirm`)
     - `PENDING -> CANCELLED` (via `POST /api/bookings/{id}/cancel`)
     - `PENDING -> EXPIRED` (via scheduler or authorized `POST /api/bookings/{id}/expire`)
     - `CONFIRMED -> CANCELLED` (via `POST /api/bookings/{id}/cancel` within cutoff)
     - `CONFIRMED -> COMPLETED` (via authorized `POST /api/bookings/{id}/complete`)
     - `CONFIRMED -> EXPIRED` (via scheduler or authorized `POST /api/bookings/{id}/expire`)
   - **Terminal State Invariant**: Once a booking reaches `CANCELLED`, `COMPLETED`, or `EXPIRED`, no transitions to active states or other terminal states are permitted. Repeated cancellation attempts are rejected with HTTP 400 Bad Request.

2. **Prohibition of Client-Supplied Status Changes**:
   - No generic `PATCH /api/bookings/{id}` endpoint accepting arbitrary status payloads is exposed.
   - Drivers can only invoke `/cancel` on their own active bookings.
   - Administrative lifecycle operations (`/confirm`, `/complete`, `/expire`) are restricted to `OPERATOR` and `ADMIN` roles using `@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")`.

3. **Configurable Driver Cancellation Cutoff**:
   - Externalized via `smartpark.booking.cancellation-cutoff-minutes` (default: 15 minutes).
   - **Boundary Semantics**: Cancellation deadline is $t_{cutoff} = \text{startTime} - \text{cutoffMinutes}$.
     - At $t \le t_{cutoff}$, cancellation is permitted.
     - For $t > t_{cutoff}$ (`now.isAfter(cutoffDeadline)`), cancellation is rejected with HTTP 400 Bad Request.

4. **Automated Expiry & Idempotent Scheduling**:
   - `BookingExpiryScheduler` runs periodically (`@Scheduled(fixedDelayString = "${smartpark.booking.expiry-check-interval-ms:60000}")`) using Spring's scheduling framework.
   - **Expiry Boundary**: Because interval reservations are half-open $[start, end)$, at $t = end$ the booking has concluded. Thus `endTime <= now` classifies an uncompleted booking as expired.
   - **Query Optimization**: Flyway migration `V3__booking_lifecycle_indexes.sql` introduces partial index `idx_bookings_active_expiry` on `(end_time, start_time) WHERE (status IN ('CONFIRMED', 'PENDING'))`.
   - **Idempotency**: Transitioning a booking to `EXPIRED` immediately drops the row from the partial index and active query criteria, ensuring subsequent scheduler runs result in 0 modifications without errors.

5. **Pessimistic Row Locking for Concurrent Lifecycle Operations**:
   - `BookingService` acquires `findByIdWithLock(id)` (`SELECT ... FOR UPDATE`) before modifying booking status.
   - If two simultaneous cancellation requests target the same booking, the transactions are serialized: exactly 1 succeeds (HTTP 200 OK) and the second re-reads the committed state, sees `status == CANCELLED`, and throws HTTP 400 Bad Request ("Booking is already cancelled").

6. **Why Redis / Kafka / Distributed Schedulers Are Deferred**:
   - PostgreSQL row-level locks and partial B-Tree indexes provide complete relational ACID consistency without the network failure modes, split-brain locks, or operational overhead of external distributed infrastructure.

### Consequences
- **Positive:**
  - Absolute protection against illegal status transitions and client tampering.
  - Zero deadlocks or race conditions on concurrent cancellations.
  - Sub-millisecond database-driven expiry queries using partial indexing.
  - Clean audit trails for every transition.
- **Negative:**
  - Scheduler runs within application process memory; distributed scheduling coordinators (e.g. ShedLock) will be added when multi-instance production clustering is deployed.

---

## ADR 012: Redis Cache-Aside Strategy and Distributed API Rate Limiting

### Status
Accepted

### Context
Phase 7 introduces Redis to optimize read performance and enforce distributed API traffic governance:
1. Parking infrastructure data (parking lots, floors, and slots) is read-heavy and relatively static compared to dynamic booking transactions. Repeatedly hitting PostgreSQL for static facility metadata adds unnecessary database load and latency.
2. Unprotected endpoints are vulnerable to burst abuse and DoS attacks. In-memory JVM rate limiters (e.g. Guava RateLimiter or `ConcurrentHashMap`) fail in multi-instance horizontally scaled deployments and drop state on restarts.
3. Crucially, relational booking consistency, GiST exclusion constraints, and transactional lifecycle invariants from Phases 1–6 must remain strictly within PostgreSQL and must not be delegated to eventual-consistency caches.

### Decision
1. **Cache-Aside Pattern for Read-Heavy Parking Data**:
   - Implemented `ParkingCacheService` wrapping Redis operations with Jackson JSON serialization.
   - **Cache Keys**:
     - Single Lot: `parking-lot:{id}` (TTL: `smartpark.cache.parking-lot-ttl-seconds`, default 300s).
     - Paginated Lots: `parking-lots:{city}:{active}:{page}:{size}`.
     - Floor Slots: `parking-slots:{lotId}:{floorId}` (TTL: `smartpark.cache.parking-slot-ttl-seconds`, default 120s).
     - Single Slot: `parking-slot:{lotId}:{floorId}:{slotId}`.
   - **Flow**: Service checks Redis first; on cache miss, fetches from PostgreSQL and asynchronously populates Redis with the configured TTL.

2. **Strict Post-Commit Cache Invalidation**:
   - Cache invalidations are deferred until database transactions have successfully committed using Spring's `TransactionSynchronizationManager.registerSynchronization()` with `afterCommit()`:
     ```java
     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
         @Override
         public void afterCommit() {
             redisTemplate.delete(key);
         }
     });
     ```
   - This prevents race conditions where a concurrent read could re-cache uncommitted dirty data or where cache is invalidated despite a transaction rollback.

3. **PostgreSQL Preserved as Authoritative Source of Truth**:
   - Redis is strictly prohibited from managing booking availability, slot reservations, or double-booking locks.
   - PostgreSQL GiST exclusion constraints (`uq_bookings_no_overlapping_active_slots`) and pessimistic slot row locking (`PESSIMISTIC_WRITE`) remain the sole authoritative arbiters of booking correctness.

4. **Distributed Atomic Fixed-Window Rate Limiter**:
   - Implemented `RedisRateLimiter` utilizing an atomic Lua script:
     ```lua
     local current = redis.call('INCR', key)
     if current == 1 then
         redis.call('EXPIRE', key, window)
     end
     local ttl = redis.call('TTL', key)
     return {current, ttl}
     ```
   - Guarantees zero race conditions on counter increment and expiration setup without distributed lock overhead.
   - **Traffic Segmentation**:
     - Authenticated requests key on `rate-limit:user:{username}` with higher quota (`authenticated-limit`, default 500 req/min).
     - Unauthenticated requests key on `rate-limit:ip:{clientIp}` (inspecting `X-Forwarded-For` with fallback to `remoteAddr`) with tighter quota (`unauthenticated-limit`, default 200 req/min).
   - **Filter & HTTP Semantics**:
     - `RateLimitingFilter` sits in the filter chain immediately after `JwtAuthenticationFilter`.
     - Excludes actuator and `/api/health` probes.
     - Adds `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset` headers to every response.
     - On limit exceeded, immediately halts filter execution and emits HTTP 429 Too Many Requests with a `Retry-After` header and structured JSON error payload.

5. **Fail-Open Resiliency and Graceful Degradation**:
   - If Redis becomes unreachable, network times out, or encounters errors:
     - `ParkingCacheService` catches all exceptions, logs a warning, and falls back to PostgreSQL reads without failing user requests.
     - `RedisRateLimiter` applies `smartpark.rate-limit.fail-open: true`, allowing legitimate application traffic to continue unimpeded during Redis maintenance or outages.

### Consequences
- **Positive:**
  - Sub-millisecond read response times for cached parking facility queries.
  - Zero risk of stale cache reads from rolled-back database transactions.
  - Horizontally scalable API rate limiting with atomic guarantees and zero local JVM state.
  - PostgreSQL transaction and exclusion constraint guarantees remain completely intact.
  - System remains fully operational even if the Redis cluster suffers complete temporary outage.
- **Negative:**
  - Extra network hop to Redis on cache reads and rate limit checks.
  - Cache memory usage must be monitored, controlled by Redis TTL policies.





