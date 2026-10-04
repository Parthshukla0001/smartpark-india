# SmartPark India — Authentication & RBAC API Testing Guide

This guide documents the step-by-step Postman / cURL testing flow to verify authentication, token issuance, and Role-Based Access Control (RBAC) in SmartPark India.

Base URL: `http://localhost:8080`

---

## 1. Step-by-Step Verification Flow

### Step 1: Register a New Driver
Public registration automatically assigns the `DRIVER` role. Client input cannot elevate permissions.

**Request:**
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/register`
- **Headers:** `Content-Type: application/json`
- **Body (JSON):**
```json
{
  "firstName": "Arjun",
  "lastName": "Mehta",
  "email": "arjun.mehta@example.in",
  "phoneNumber": "+919876543210",
  "password": "SecurePassword@123"
}
```

**Expected Response (`201 Created`):**
```json
{
  "id": 1,
  "firstName": "Arjun",
  "lastName": "Mehta",
  "email": "arjun.mehta@example.in",
  "phoneNumber": "+919876543210",
  "role": "DRIVER",
  "active": true,
  "createdAt": "2026-10-04T17:20:00Z"
}
```
*(Notice: `password` and `passwordHash` are never returned)*

---

### Step 2: Login to Obtain JWT Token

**Request:**
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body (JSON):**
```json
{
  "email": "arjun.mehta@example.in",
  "password": "SecurePassword@123"
}
```

**Expected Response (`200 OK`):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiRFJJVkVSIiwiZW1haWwiOiJhcmp1bi5tZWh0YUBleGFtcGxlLmluIiwic3ViIjoiMSIsImlhdCI6MTc5MTEzNDIwMCwiZXhwIjoxNzkxMjIwNjAwfQ...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "userId": 1,
  "email": "arjun.mehta@example.in",
  "role": "DRIVER",
  "firstName": "Arjun",
  "lastName": "Mehta"
}
```

---

### Step 3: Call Protected Endpoint (`/api/security/me`)
Copy the `accessToken` from Step 2.

**Request:**
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/security/me`
- **Headers:**
  - `Authorization: Bearer <accessToken>`

**Expected Response (`200 OK`):**
```json
{
  "id": 1,
  "firstName": "Arjun",
  "lastName": "Mehta",
  "email": "arjun.mehta@example.in",
  "phoneNumber": "+919876543210",
  "role": "DRIVER",
  "active": true,
  "createdAt": "2026-10-04T17:20:00Z"
}
```

---

### Step 4: Access Authorized Role Endpoint (`/api/security/driver`)

**Request:**
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/security/driver`
- **Headers:**
  - `Authorization: Bearer <accessToken>`

**Expected Response (`200 OK`):**
```json
{
  "access": "GRANTED",
  "message": "Welcome to Driver portal verification endpoint",
  "user": "arjun.mehta@example.in"
}
```

---

### Step 5: Demonstrate Forbidden Role Request (`403 Forbidden`)
Attempt to access an Administrator-only endpoint using the Driver's JWT token.

**Request:**
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/security/admin`
- **Headers:**
  - `Authorization: Bearer <accessToken>`

**Expected Response (`403 Forbidden`):**
```json
{
  "timestamp": "2026-10-04T17:20:05Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: you do not have permission to access this resource",
  "path": "/api/security/admin"
}
```

---

### Step 6: Demonstrate Unauthorized Request (`401 Unauthorized`)
Call a protected endpoint without an Authorization header or with an invalid token.

**Request:**
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/security/me`
*(No Authorization header)*

**Expected Response (`401 Unauthorized`):**
```json
{
  "timestamp": "2026-10-04T17:20:10Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/security/me"
}
```

---

### Step 7: Demonstrate Brute-Force Lockout (`429 Too Many Requests`)
Submit 5 consecutive failed login attempts with wrong credentials for the same email address. The 6th attempt will be blocked immediately:

**Expected Response (`429 Too Many Requests`):**
```json
{
  "timestamp": "2026-10-04T17:20:15Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many failed login attempts. Account temporarily locked. Please try again later.",
  "path": "/api/auth/login"
}
```

---

## 2. Parking Management APIs (Phase 3)

### Role Permissions Matrix

| Endpoint | Method | DRIVER | OPERATOR | ADMIN | Description |
|---|---|:---:|:---:|:---:|---|
| `/api/parking-lots` | `POST` | ❌ | ✅ | ✅ | Create new parking lot |
| `/api/parking-lots` | `GET` | ✅ (Active only) | ✅ (All) | ✅ (All) | Search/filter/paginate lots |
| `/api/parking-lots/{id}` | `GET` | ✅ (Active only) | ✅ | ✅ | Get parking lot details |
| `/api/parking-lots/{id}` | `PUT` | ❌ | ✅ | ✅ | Update parking lot details |
| `/api/parking-lots/{id}` | `DELETE` | ❌ | ❌ | ✅ | Deactivate parking lot (soft-delete) |
| `/api/parking-lots/{lotId}/floors` | `POST` | ❌ | ✅ | ✅ | Create floor under parking lot |
| `/api/parking-lots/{lotId}/floors` | `GET` | ✅ | ✅ | ✅ | List floors for a parking lot |
| `/api/parking-lots/{lotId}/floors/{floorId}` | `GET` | ✅ | ✅ | ✅ | Get floor by ID |
| `/api/parking-lots/{lotId}/floors/{floorId}` | `PUT` | ❌ | ✅ | ✅ | Update floor details |
| `/api/parking-lots/{lotId}/floors/{floorId}` | `DELETE` | ❌ | ❌ | ✅ | Delete empty floor |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots` | `POST` | ❌ | ✅ | ✅ | Create slot under floor |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots` | `GET` | ✅ | ✅ | ✅ | List slots on a floor |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots/{slotId}` | `GET` | ✅ | ✅ | ✅ | Get slot by ID |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots/{slotId}` | `PUT` | ❌ | ✅ | ✅ | Update slot metadata |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots/{slotId}/status` | `PATCH` | ❌ | ✅ | ✅ | Change operational status |
| `/api/parking-lots/{lotId}/floors/{floorId}/slots/{slotId}` | `DELETE` | ❌ | ✅ | ✅ | Deactivate slot (`OUT_OF_SERVICE`) |

---

### 2.1 Parking Lot Examples

#### 1. Create Parking Lot
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/parking-lots`
- **Headers:**
  - `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
  - `Content-Type: application/json`
- **Body:**
```json
{
  "name": "Esplanade Smart Hub",
  "address": "Plot 12, Rasulgarh",
  "city": "Bhubaneswar",
  "state": "Odisha",
  "pincode": "751010",
  "latitude": 20.2961000,
  "longitude": 85.8245000,
  "openingTime": "06:00:00",
  "closingTime": "23:00:00"
}
```
- **Response (`201 Created`):**
```json
{
  "id": 1,
  "name": "Esplanade Smart Hub",
  "address": "Plot 12, Rasulgarh",
  "city": "Bhubaneswar",
  "state": "Odisha",
  "pincode": "751010",
  "latitude": 20.2961000,
  "longitude": 85.8245000,
  "openingTime": "06:00:00",
  "closingTime": "23:00:00",
  "active": true,
  "createdAt": "2026-10-04T17:30:00Z",
  "updatedAt": "2026-10-04T17:30:00Z"
}
```

#### 2. List & Filter Parking Lots (with Pagination)
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/parking-lots?city=Bhubaneswar&state=Odisha&active=true&page=0&size=10&sortBy=name&sortDirection=asc`
- **Headers:** `Authorization: Bearer <JWT>`
- **Response (`200 OK`):**
```json
{
  "content": [
    {
      "id": 1,
      "name": "Esplanade Smart Hub",
      "address": "Plot 12, Rasulgarh",
      "city": "Bhubaneswar",
      "state": "Odisha",
      "pincode": "751010",
      "latitude": 20.2961000,
      "longitude": 85.8245000,
      "openingTime": "06:00:00",
      "closingTime": "23:00:00",
      "active": true,
      "createdAt": "2026-10-04T17:30:00Z",
      "updatedAt": "2026-10-04T17:30:00Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

#### 3. Update Parking Lot
- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/parking-lots/1`
- **Headers:**
  - `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
  - `Content-Type: application/json`
- **Body:**
```json
{
  "name": "Esplanade Smart Hub - Main",
  "address": "Plot 12, Rasulgarh",
  "city": "Bhubaneswar",
  "state": "Odisha",
  "pincode": "751010",
  "latitude": 20.2961000,
  "longitude": 85.8245000,
  "openingTime": "06:00:00",
  "closingTime": "23:30:00",
  "active": true
}
```

#### 4. Deactivate Parking Lot (Soft-Delete)
- **Method:** `DELETE`
- **URL:** `http://localhost:8080/api/parking-lots/1`
- **Headers:** `Authorization: Bearer <ADMIN_JWT>`
- **Response (`204 No Content`)**

---

### 2.2 Parking Floor Examples

#### 1. Create Floor under Parking Lot
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/parking-lots/1/floors`
- **Headers:**
  - `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
  - `Content-Type: application/json`
- **Body:**
```json
{
  "floorNumber": 1,
  "name": "Ground Floor"
}
```
- **Response (`201 Created`):**
```json
{
  "id": 10,
  "parkingLotId": 1,
  "floorNumber": 1,
  "name": "Ground Floor",
  "createdAt": "2026-10-04T17:35:00Z",
  "updatedAt": "2026-10-04T17:35:00Z"
}
```

#### 2. List Floors for Parking Lot
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/parking-lots/1/floors`
- **Headers:** `Authorization: Bearer <JWT>`
- **Response (`200 OK`):**
```json
[
  {
    "id": 10,
    "parkingLotId": 1,
    "floorNumber": 1,
    "name": "Ground Floor",
    "createdAt": "2026-10-04T17:35:00Z",
    "updatedAt": "2026-10-04T17:35:00Z"
  }
]
```

---

### 2.3 Parking Slot Examples

#### 1. Create Slot under Floor
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/parking-lots/1/floors/10/slots`
- **Headers:**
  - `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
  - `Content-Type: application/json`
- **Body:**
```json
{
  "slotNumber": "G-01",
  "slotType": "EV"
}
```
- **Response (`201 Created`):**
```json
{
  "id": 101,
  "parkingFloorId": 10,
  "slotNumber": "G-01",
  "slotType": "EV",
  "status": "AVAILABLE",
  "createdAt": "2026-10-04T17:40:00Z",
  "updatedAt": "2026-10-04T17:40:00Z"
}
```

#### 2. Update Slot Operational Status
- **Method:** `PATCH`
- **URL:** `http://localhost:8080/api/parking-lots/1/floors/10/slots/101/status`
- **Headers:**
  - `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
  - `Content-Type: application/json`
- **Body:**
```json
{
  "status": "OUT_OF_SERVICE"
}
```
- **Response (`200 OK`):**
```json
{
  "id": 101,
  "parkingFloorId": 10,
  "slotNumber": "G-01",
  "slotType": "EV",
  "status": "OUT_OF_SERVICE",
  "createdAt": "2026-10-04T17:40:00Z",
  "updatedAt": "2026-10-04T17:42:00Z"
}
```

#### 3. Deactivate Slot
- **Method:** `DELETE`
- **URL:** `http://localhost:8080/api/parking-lots/1/floors/10/slots/101`
- **Headers:** `Authorization: Bearer <ADMIN_OR_OPERATOR_JWT>`
- **Response (`204 No Content`)** *(Safely sets status to `OUT_OF_SERVICE`)*

