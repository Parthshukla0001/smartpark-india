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
