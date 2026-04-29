# Intact ID — Partner Integration API Reference

> **Base URL (Production):** `https://api.intact-id.com`
> **Base URL (Sandbox):** `https://sandbox.intact-id.com`
> **API Version:** `v1`
> **Docs:** `https://docs.intact-id.com`

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Authentication](#authentication)
3. [Environments](#environments)
4. [KYC Tiers & Pricing](#kyc-tiers--pricing)
5. [Company Registration](#company-registration)
6. [KYC Verification API](#kyc-verification-api)
7. [Verification Status](#verification-status)
8. [Webhooks](#webhooks)
9. [Iframe Widget](#iframe-widget)
10. [Rate Limits](#rate-limits)
11. [Error Reference](#error-reference)
12. [Response Format](#response-format)

---

## Getting Started

### Quickstart

1. **Register your company** at `https://dashboard.intact-id.com` or via the [registration API](#company-registration).
2. **Wait for approval** — our team reviews applications within 1–2 business days.
3. **Get your API keys** from the dashboard (separate keys for sandbox and production).
4. **Submit a test verification** using the sandbox environment.
5. **Configure webhooks** to receive real-time status updates.
6. **Go live** — swap your sandbox key for the production key.

### Base Request

All API requests must be made over HTTPS. HTTP requests will be rejected.

```http
POST /api/v1/kyc/verify/TIER_1
Host: api.intact-id.com
X-API-Key: ik_live_xxxxxxxxxxxx
X-API-Secret: sk_live_xxxxxxxxxxxx
Content-Type: multipart/form-data
```

---

## Authentication

Intact ID uses **API key authentication** for verification endpoints and **JWT bearer tokens** for dashboard/management endpoints.

### API Key Authentication

Used for all KYC verification requests.

| Header | Description |
|--------|-------------|
| `X-API-Key` | Your API key (`ik_live_...` or `ik_dev_...`) |
| `X-API-Secret` | Your API secret (`sk_live_...` or `sk_dev_...`) |

API keys are **environment-scoped** — a live key cannot be used to hit sandbox endpoints and vice versa.

```http
POST /api/v1/kyc/verify/TIER_1
X-API-Key: ik_live_abc123
X-API-Secret: sk_live_xyz789
```

### JWT Bearer Token

Used for management endpoints (querying verification history, configuring webhooks, etc.). Obtain a token by logging in with your dashboard credentials.

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Token Lifetimes:**
- Access token: 15 minutes
- Refresh token: 7 days

#### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin@yourcompany.com",
  "password": "yourpassword"
}
```

**Response:**
```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "userId": "usr_abc123",
    "email": "admin@yourcompany.com",
    "roles": ["COMPANY"],
    "loginAt": "2024-01-15T10:30:00"
  }
}
```

#### Logout

```http
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "eyJ..."
}
```

### Widget Session Token

Used exclusively with the [Iframe Widget](#iframe-widget). Your backend obtains this token server-side and passes it to your frontend. See the widget section for details.

---

## Environments

| Environment | Purpose | Key Prefix |
|-------------|---------|------------|
| **Sandbox (DEV)** | Testing and integration | `ik_dev_` / `sk_dev_` |
| **Production (PROD)** | Live verifications | `ik_live_` / `sk_live_` |

Sandbox verifications do not count toward billing. Use realistic test data in sandbox — the system runs real OCR and face-matching against your uploads.

**Sandbox endpoint prefix:** `/api/v1/kyc/dev/verify/{tier}`
**Production endpoint prefix:** `/api/v1/kyc/verify/{tier}`

---

## KYC Tiers & Pricing

### GET /api/v1/tiers

Returns all available verification tiers. No authentication required.

```http
GET /api/v1/tiers
```

**Response:**
```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "tiers": [
      {
        "tierKey": "TIER_1",
        "name": "Basic Document Verification",
        "priceRange": "$0.30 – $0.40",
        "features": [
          "OCR data extraction",
          "Selfie capture",
          "Basic liveness check",
          "Match selfie to ID"
        ],
        "exclusions": [
          "No government database check",
          "No fraud detection"
        ]
      }
    ]
  }
}
```

### GET /api/v1/tiers/{tierKey}

Returns details for a single tier.

```http
GET /api/v1/tiers/TIER_2
```

### Tier Comparison

| Feature | TIER_1 | TIER_2 | TIER_3 |
|---------|--------|--------|--------|
| OCR data extraction | ✅ | ✅ | ✅ |
| Selfie + liveness check | ✅ | ✅ | ✅ |
| Selfie-to-ID match | ✅ | ✅ | ✅ |
| Enhanced video liveness | ❌ | ✅ | ✅ |
| Document fraud detection | ❌ | ✅ | ✅ |
| Government DB verification (IPRS) | ❌ | ✅ | ✅ |
| ID expiration check | ❌ | ✅ | ✅ |
| Duplicate detection | ❌ | ✅ | ✅ |
| Sanctions screening (OFAC, UN, EU) | ❌ | ❌ | ✅ |
| PEP checks | ❌ | ❌ | ✅ |
| Adverse media screening | ❌ | ❌ | ✅ |
| Risk scoring | ❌ | ❌ | ✅ |
| Manual review queue | ❌ | ❌ | ✅ |
| **Price per verification** | **$0.025** | **$0.175** | **$0.475** |

---

## Company Registration

### POST /api/public/companies/register

Submit a company application. No authentication required.

```http
POST /api/public/companies/register
Content-Type: application/json

{
  "legalName": "Acme Financial Ltd",
  "tradingName": "Acme Pay",
  "registrationNumber": "PVT-12345678",
  "email": "contact@acmepay.com",
  "phone": "+254700000000",
  "website": "https://acmepay.com",
  "country": "KE",
  "businessType": "digital_lender",
  "contactPersonName": "Jane Doe",
  "contactPersonTitle": "CTO",
  "contactPersonEmail": "jane@acmepay.com",
  "contactPersonPhone": "+254711111111"
}
```

**`businessType` values:** `digital_lender` | `neobank` | `crypto_exchange`

**Response:**
```json
{
  "responseCode": 201,
  "success": true,
  "data": {
    "applicationId": "APP-20240115-001",
    "status": "PENDING_APPROVAL",
    "message": "Application submitted. You will be notified by email when reviewed."
  }
}
```

### GET /api/public/applications/{appId}/status

Check your application status without authentication.

```http
GET /api/public/applications/APP-20240115-001/status
```

**Application statuses:** `PENDING_APPROVAL` → `APPROVED` | `REJECTED`

---

## KYC Verification API

### Submit a Verification

#### Production

```http
POST /api/v1/kyc/verify/{tier}
X-API-Key: ik_live_xxxxxxxxxxxx
X-API-Secret: sk_live_xxxxxxxxxxxx
Content-Type: multipart/form-data
```

#### Sandbox

```http
POST /api/v1/kyc/dev/verify/{tier}
X-API-Key: ik_dev_xxxxxxxxxxxx
X-API-Secret: sk_dev_xxxxxxxxxxxx
Content-Type: multipart/form-data
```

`{tier}` is one of: `TIER_1` | `TIER_2` | `TIER_3`

### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `firstName` | string | ✅ | Applicant's first name |
| `lastName` | string | ✅ | Applicant's last name |
| `dateOfBirth` | string | ✅ | ISO 8601 date — `YYYY-MM-DD` |
| `idNumber` | string | ✅ | National ID number (8 digits) |
| `idType` | string | ✅ | See ID types below |
| `nationality` | string | ✅ | ISO 3166-1 alpha-2 country code (e.g. `KE`) |
| `countryOfResidence` | string | ✅ | ISO 3166-1 alpha-2 country code |
| `phone` | string | ✅ | Phone number with country code |
| `email` | string | ✅ | Applicant email — used for result notifications |
| `verificationType` | string | ✅ | `individual` or `business` |
| `selfie` | file | ✅ | Selfie photo (JPEG/PNG, max 10 MB) |
| `idFront` | file | ✅ | Front of ID document (JPEG/PNG, max 10 MB) |
| `idBack` | file | ⚠️ | Back of ID (required for national ID) |
| `proofOfAddress` | file | ❌ | Utility bill or bank statement (optional) |
| `webhookUrl` | string | ❌ | Override company webhook URL for this request |

**Supported `idType` values:** `national_id` | `passport` | `driving_license`

### Example (cURL)

```bash
curl -X POST https://api.intact-id.com/api/v1/kyc/verify/TIER_1 \
  -H "X-API-Key: ik_live_xxxxxxxxxxxx" \
  -H "X-API-Secret: sk_live_xxxxxxxxxxxx" \
  -F "firstName=Jane" \
  -F "lastName=Doe" \
  -F "dateOfBirth=1990-05-15" \
  -F "idNumber=12345678" \
  -F "idType=national_id" \
  -F "nationality=KE" \
  -F "countryOfResidence=KE" \
  -F "phone=+254700000000" \
  -F "email=jane@example.com" \
  -F "verificationType=individual" \
  -F "selfie=@/path/to/selfie.jpg" \
  -F "idFront=@/path/to/id_front.jpg" \
  -F "idBack=@/path/to/id_back.jpg"
```

### Submit Response

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "verificationId": "VRF-20240115-A1B2C3",
    "status": "SUBMITTED",
    "tier": "TIER_1",
    "estimatedCompletion": "2024-01-15T10:35:00",
    "webhookUrl": "https://yourapp.com/webhooks/intact",
    "message": "Verification submitted successfully. Processing will begin shortly."
  }
}
```

> **Important:** Save the `verificationId`. You will need it to poll for status or to correlate incoming webhook events.

---

## Verification Status

### GET /api/v1/kyc/verify/{verificationId}

Poll the status of a verification. Requires JWT bearer token.

```http
GET /api/v1/kyc/verify/VRF-20240115-A1B2C3
Authorization: Bearer eyJ...
```

### Status Response

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "verificationId": "VRF-20240115-A1B2C3",
    "status": "COMPLETED",
    "tier": "TIER_1",
    "verificationType": "individual",
    "overallDecision": "approved",
    "riskScore": 0.12,
    "failureReason": null,
    "errorDetails": null,
    "createdAt": "2024-01-15T10:30:00",
    "completedAt": "2024-01-15T10:31:45",
    "customerData": {
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "phone": "+254700000000",
      "nationality": "KE",
      "idNumber": "12345678",
      "idType": "national_id"
    },
    "steps": [...],
    "documents": [...]
  }
}
```

### Verification Statuses

| Status | Description |
|--------|-------------|
| `SUBMITTED` | Received, queued for processing |
| `PROCESSING` | Actively being verified |
| `COMPLETED` | Finished — check `overallDecision` |
| `REJECTED` | Verification failed a check |
| `FAILED` | System or processing error |
| `PENDING_APPROVAL` | Flagged for manual review (TIER_3) |

### Overall Decision Values

| Decision | Meaning |
|----------|---------|
| `approved` | All checks passed |
| `rejected` | One or more checks failed |
| `manual_review` | Routed to human reviewer |
| `error` | Processing error occurred |

### List Verifications

```http
GET /api/v1/kyc/verifications?status=COMPLETED&tier=TIER_2&page=0&size=20
Authorization: Bearer eyJ...
```

**Query Parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Filter by status |
| `tier` | string | Filter by tier |
| `fromDate` | string | ISO 8601 start date |
| `toDate` | string | ISO 8601 end date |
| `search` | string | Search by name, email, ID number |
| `page` | integer | Page number (default: 0) |
| `size` | integer | Page size (default: 20) |

---

## Webhooks

Webhooks deliver real-time verification results to your server. Configure a webhook URL in your dashboard or per-request via the `webhookUrl` field.

### Configuration

Set your webhook endpoint in the dashboard or via API:

```http
PUT /api/companies/{id}/communication/webhook
Authorization: Bearer eyJ...
Content-Type: application/json

{
  "webhookUrl": "https://yourapp.com/webhooks/intact",
  "webhookEnabled": true,
  "emailNotifications": true,
  "smsNotifications": false,
  "payloadFields": {
    "includeIds": true,
    "includeTier": true,
    "includeTimestamps": true,
    "includeResults": true,
    "includeRawScores": false,
    "includeApplicant": true,
    "includeVerificationId": true,
    "includeCompanyId": true,
    "includeCompanyName": true,
    "includeRejectionReason": true
  }
}
```

### Test Your Webhook

```http
POST /api/companies/{id}/communication/webhook/test
Authorization: Bearer eyJ...
```

This sends a `test.webhook` event to your configured URL.

### Rotate Webhook Secret

```http
POST /api/companies/{id}/communication/webhook/rotate-secret
Authorization: Bearer eyJ...
```

The new secret is returned **once** in the response. Store it immediately.

### Webhook Event Payload

```json
{
  "eventType": "verification.completed",
  "eventId": "evt_abc123xyz",
  "timestamp": "2024-01-15T10:31:45",
  "companyId": "CMP-001",
  "companyName": "Acme Pay",
  "verificationId": "VRF-20240115-A1B2C3",
  "apiVersion": "v1",
  "data": {
    "verification_id": "VRF-20240115-A1B2C3",
    "status": "COMPLETED",
    "overall_decision": "approved",
    "tier": "TIER_1",
    "verification_type": "individual",
    "completed_at": "2024-01-15T10:31:45",
    "started_at": "2024-01-15T10:30:10",
    "rejection_reason": null,
    "rejection_details": null,
    "applicant": {
      "email": "jane@example.com",
      "phone": "+254700000000",
      "name": "Jane Doe"
    },
    "results": {
      "face_screening": {
        "status": "completed",
        "decision": "approved",
        "checks": {
          "face_detected": true,
          "liveness_passed": true,
          "face_match_score": 0.97,
          "quality_score": 0.88
        }
      }
    }
  }
}
```

### Event Types

| Event | When it fires |
|-------|--------------|
| `verification.completed` | Verification reaches a final state (approved, rejected, manual_review) |
| `verification.failed` | Processing error occurred |
| `test.webhook` | Triggered manually via the test endpoint |

### Rejection Reason Codes

| Code | Description |
|------|-------------|
| `FaceNotDetectedError` | No face found in selfie |
| `LivenessCheckFailed` | Liveness test not passed |
| `FaceMatchFailed` | Selfie doesn't match ID photo |
| `ImageQualityTooLow` | Image is blurry or too dark |
| `SystemError` | Internal processing error |

### Verifying Webhook Signatures

Every webhook request includes an `X-Webhook-Signature` header. Verify it to ensure the request came from Intact ID.

```http
X-Webhook-Signature: sha256=a1b2c3d4e5f6...
X-Event-Type: verification.completed
User-Agent: IntactID-Webhook/1.0
```

**Node.js example:**
```javascript
const crypto = require('crypto');

function verifyWebhook(payload, signature, secret) {
  const expectedSig = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(JSON.stringify(payload))
    .digest('hex');

  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expectedSig)
  );
}
```

**Python example:**
```python
import hmac, hashlib, json

def verify_webhook(payload: dict, signature: str, secret: str) -> bool:
    expected = 'sha256=' + hmac.new(
        secret.encode(),
        json.dumps(payload).encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(signature, expected)
```

### Retry Policy

Failed deliveries (non-2xx response or timeout) are retried:

| Attempt | Delay |
|---------|-------|
| 1st retry | 60 seconds |
| 2nd retry | 5 minutes |
| 3rd retry | 15 minutes |

Your endpoint should respond with HTTP `200` within **30 seconds**. Process asynchronously if needed.

---

## Iframe Widget

The Intact ID Widget lets you embed KYC directly into your web app without building your own capture UI. It handles selfie capture, document upload, processing, and displays the result.

### How It Works

```
Your Backend  →  POST /api/v1/kyc/sessions  →  Gets session token (15 min)
Your Frontend →  Injects widget with token    →  User completes KYC
Widget        →  postMessage to your app      →  kyc:completed / kyc:failed
```

### Step 1 — Get a Session Token (Server-Side)

Your **backend** calls this endpoint with your API key. Never expose your API key to the browser.

```http
POST /api/v1/kyc/sessions
X-API-Key: ik_live_xxxxxxxxxxxx
X-API-Secret: sk_live_xxxxxxxxxxxx
Content-Type: application/json

{
  "tier": "TIER_1",
  "environment": "PROD"
}
```

**Response:**
```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "sessionToken": "eyJ...",
    "expiresInSeconds": 900,
    "tier": "TIER_1",
    "environment": "PROD"
  }
}
```

### Step 2 — Embed the Widget (Frontend)

Add the embed script to your page:

```html
<script src="https://widget.intact-id.com/kyc.js"></script>
```

Then initialize:

```javascript
const kyc = IntactKYC({
  sessionToken: '<token-from-your-backend>',
  widgetUrl: 'https://widget.intact-id.com',
  onComplete: (result) => {
    if (result.status === 'approved') {
      // User passed KYC
      console.log('Verification ID:', result.verificationId);
    } else {
      console.log('Status:', result.status, 'Reason:', result.reason);
    }
  }
});

kyc.open();
```

### PostMessage Events

The widget emits events to the parent window via `window.postMessage`:

| Event | Payload | Description |
|-------|---------|-------------|
| `kyc:started` | `{}` | User opened the widget |
| `kyc:completed` | `{ verificationId, status, decision }` | Verification reached a final state |
| `kyc:failed` | `{ error, reason }` | Fatal error occurred |
| `kyc:closed` | `{}` | User dismissed the widget |

**Listening to events:**
```javascript
window.addEventListener('message', (event) => {
  if (event.origin !== 'https://widget.intact-id.com') return;

  const { type, payload } = event.data;

  switch (type) {
    case 'kyc:completed':
      console.log('Done:', payload.verificationId, payload.status);
      break;
    case 'kyc:closed':
      console.log('User closed the widget');
      break;
  }
});
```

### Widget Session Token Limits

- Tokens expire after **15 minutes**
- Each token is scoped to one company, one tier, and one environment
- Tokens are single-use by design — generate a new token per session

---

## Rate Limits

Rate limits are enforced per company, per environment. Exceeding a limit returns HTTP `429`.

### Default Limits

| Window | Sandbox (DEV) | Production (PROD) |
|--------|--------------|------------------|
| Per hour | Configurable | Configurable |
| Per day | Configurable | Configurable |
| Per month | Configurable | Configurable |

Contact support to request limit increases.

### 429 Response

```json
{
  "responseCode": 429,
  "success": false,
  "errorMessage": "Rate limit exceeded. Retry after 3600 seconds.",
  "data": null
}
```

Implement **exponential backoff** when you receive a 429.

### Checking Your Usage

```http
GET /api/companies/api-keys/usage
Authorization: Bearer eyJ...
```

**Response:**
```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "dev": {
      "totalRequests": 142,
      "totalSuccessful": 138,
      "totalFailed": 4,
      "limitPerHour": 100,
      "limitPerDay": 500,
      "limitPerMonth": 5000
    },
    "prod": {
      "totalRequests": 2891,
      "totalSuccessful": 2876,
      "totalFailed": 15,
      "limitPerHour": 500,
      "limitPerDay": 5000,
      "limitPerMonth": 50000
    }
  }
}
```

---

## Error Reference

All error responses follow the [standard response format](#response-format).

### HTTP Status Codes

| Code | Name | When |
|------|------|------|
| `200` | OK | Success |
| `201` | Created | Resource created |
| `400` | Bad Request | Invalid parameters or missing required fields |
| `401` | Unauthorized | Missing, invalid, or expired credentials |
| `403` | Forbidden | Valid credentials but insufficient permissions |
| `404` | Not Found | Resource not found |
| `409` | Conflict | Duplicate resource (e.g. already registered) |
| `429` | Too Many Requests | Rate limit exceeded |
| `500` | Internal Server Error | Unexpected server error |

### Common Error Scenarios

**Missing API key:**
```json
{
  "responseCode": 401,
  "success": false,
  "errorMessage": "API key is required",
  "isInternalError": false
}
```

**Invalid session token (widget):**
```json
{
  "responseCode": 401,
  "success": false,
  "errorMessage": "Session token is invalid or expired",
  "isInternalError": false
}
```

**Duplicate verification:**
```json
{
  "responseCode": 409,
  "success": false,
  "errorMessage": "A verification for this ID number is already in progress",
  "isInternalError": false
}
```

**File too large (>10 MB):**
```json
{
  "responseCode": 400,
  "success": false,
  "errorMessage": "File size exceeds the maximum allowed size of 10MB",
  "isInternalError": false
}
```

---

## Response Format

All API responses use a consistent envelope:

```json
{
  "responseCode": 200,
  "responseMessage": "Success",
  "success": true,
  "data": { ... },
  "errorMessage": null,
  "isInternalError": false
}
```

| Field | Type | Description |
|-------|------|-------------|
| `responseCode` | integer | HTTP status code mirrored in body |
| `responseMessage` | string | Human-readable status message |
| `success` | boolean | `true` if the request succeeded |
| `data` | object \| null | Response payload |
| `errorMessage` | string \| null | Present when `success` is `false` |
| `isInternalError` | boolean | `true` only for 5xx errors |

Paginated responses wrap their lists:

```json
{
  "responseCode": 200,
  "success": true,
  "data": {
    "content": [ ... ],
    "totalElements": 150,
    "totalPages": 8,
    "page": 0,
    "size": 20
  }
}
```

---

## File Upload Guidelines

| Requirement | Value |
|-------------|-------|
| Accepted formats | JPEG, PNG |
| Max file size | 10 MB per file |
| Max request size | 50 MB total |
| Selfie requirements | Face clearly visible, good lighting, no sunglasses |
| ID requirements | All text readable, document flat, no glare |

---

## Applicant Email Notifications

When enabled, Intact ID automatically emails your applicants with their verification result. Configure this per company in the dashboard.

| Setting | Default | Description |
|---------|---------|-------------|
| `notifyApplicantOnApproval` | `true` | Email sent when KYC is approved |
| `notifyApplicantOnRejection` | `true` | Email sent with rejection reason |
| `notifyApplicantOnPendingReview` | `false` | Email sent when routed to manual review |

The applicant's email address is taken from the `email` field in the verification submission. These settings can only be changed by Intact ID administrators.

---

## SDK Examples

### Node.js

```javascript
const FormData = require('form-data');
const fs = require('fs');
const axios = require('axios');

const form = new FormData();
form.append('firstName', 'Jane');
form.append('lastName', 'Doe');
form.append('dateOfBirth', '1990-05-15');
form.append('idNumber', '12345678');
form.append('idType', 'national_id');
form.append('nationality', 'KE');
form.append('countryOfResidence', 'KE');
form.append('phone', '+254700000000');
form.append('email', 'jane@example.com');
form.append('verificationType', 'individual');
form.append('selfie', fs.createReadStream('./selfie.jpg'));
form.append('idFront', fs.createReadStream('./id_front.jpg'));
form.append('idBack', fs.createReadStream('./id_back.jpg'));

const response = await axios.post(
  'https://api.intact-id.com/api/v1/kyc/verify/TIER_1',
  form,
  {
    headers: {
      ...form.getHeaders(),
      'X-API-Key': process.env.INTACT_API_KEY,
      'X-API-Secret': process.env.INTACT_API_SECRET
    }
  }
);

console.log(response.data.data.verificationId);
```

### Python

```python
import requests
import os

url = "https://api.intact-id.com/api/v1/kyc/verify/TIER_1"

fields = {
    "firstName": "Jane",
    "lastName": "Doe",
    "dateOfBirth": "1990-05-15",
    "idNumber": "12345678",
    "idType": "national_id",
    "nationality": "KE",
    "countryOfResidence": "KE",
    "phone": "+254700000000",
    "email": "jane@example.com",
    "verificationType": "individual",
}

files = {
    "selfie": open("selfie.jpg", "rb"),
    "idFront": open("id_front.jpg", "rb"),
    "idBack": open("id_back.jpg", "rb"),
}

headers = {
    "X-API-Key": os.environ["INTACT_API_KEY"],
    "X-API-Secret": os.environ["INTACT_API_SECRET"],
}

response = requests.post(url, data=fields, files=files, headers=headers)
print(response.json()["data"]["verificationId"])
```

---

## Support

| Channel | Contact |
|---------|---------|
| Email | support@intact-id.com |
| Dashboard | https://dashboard.intact-id.com |
| Docs | https://docs.intact-id.com |

---

*API Version: v1 — Last updated: April 2026*
