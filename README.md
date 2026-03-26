# FleetGuard — Fleet Underwriting Risk Scoring

A Spring Boot application with a Thymeleaf web UI that automates insurance underwriting decisions for commercial vehicle fleets. Customers submit their fleet profile, get an instant risk assessment, and receive a policy — or have their application reviewed by an admin.

## Features

- Web UI built with Thymeleaf and Bootstrap 5
- Landing page with plan selection (Basic, Premium, Diamond)
- Underwriting form with phone number validation
- Risk scoring engine using a sigmoid-normalised weighted sum
- Customer workflow: instant policy for LOW risk, admin review queue for MEDIUM/HIGH
- Admin portal with login, review queue, approve/reject actions
- Customer ID assigned on policy issuance
- Policy lookup by Customer ID or Policy Number
- PostgreSQL persistence (H2 in-memory for local dev)

## User Roles

### Customer
1. Go to the landing page and choose a plan
2. Fill in your fleet details (including phone number)
3. Submit to get your risk assessment:
   - **LOW risk** — click "Accept & Generate Policy" to issue your policy instantly
   - **MEDIUM / HIGH risk** — application goes to the admin review queue
4. On policy issuance you receive a **Customer ID** (e.g. `CUST-2026-0001`)
5. Use your Customer ID or Policy Number at `/policy/lookup` to view your coverage

### Admin
- Login at `/admin/login` (default: `admin` / `admin123`)
- **Review Queue** (`/admin/queue`) — pending MEDIUM/HIGH risk applications; approve or reject each
- **All Submissions** (`/admin/all`) — full history with workflow status

## Workflow

```
Customer submits quote
        │
        ├── LOW risk  ──► PENDING_CUSTOMER_ACCEPTANCE
        │                        │
        │               Customer clicks Accept
        │                        │
        │                   POLICY_ISSUED ──► Customer ID assigned
        │
        └── MEDIUM / HIGH risk ──► PENDING_ADMIN_REVIEW
                                          │
                              Admin approves / rejects
                                 │              │
                          POLICY_ISSUED      REJECTED
```

## Risk Scoring

Each submission is scored across four dimensions:

| Dimension | Factors |
|---|---|
| Business Stability | Company age, credit score, war zone operations |
| Fleet Condition | Average vehicle age, primary vehicle type |
| Driver Pool | Average driver age, pool size |
| Claims History | Frequency, severity, at-fault rate |

Dimension scores are summed and passed through a **sigmoid function** to produce a normalised score between 0 and 1:

| Score | Category | Action | Premium Multiplier |
|---|---|---|---|
| ≤ 0.35 | LOW | APPROVE | 1.0× – 1.5× |
| ≤ 0.65 | MEDIUM | REVIEW | 1.5× – 2.0× |
| > 0.65 | HIGH | REJECT | 2.0× – 2.5× |

## REST API

### `POST /api/v1/underwriting/score`

**Request**
```json
{
  "businessInfo": {
    "companyName": "Acme Logistics",
    "phoneNumber": "+441234567890",
    "yearsInOperation": 8,
    "creditScore": 720,
    "warZone": false
  },
  "fleetDetails": {
    "totalVehicles": 20,
    "averageVehicleAgeYears": 3.5,
    "primaryVehicleType": "VAN"
  },
  "driverPool": {
    "totalDrivers": 25,
    "averageDriverAge": 34.0
  },
  "claimsHistory": {
    "claimsLast3Years": 2,
    "totalClaimAmount": 15000,
    "atFaultCount": 1
  }
}
```

**Response**
```json
{
  "riskScore": 0.21,
  "riskCategory": "LOW",
  "recommendedAction": "APPROVE",
  "premiumMultiplier": 1.32,
  "decisionFactors": [
    "Low claims frequency — minor loss history present"
  ]
}
```

## Configuration

All scoring weights and thresholds are externalized in `application.properties`.

```properties
# Sigmoid tuning
risk.sigmoid.steepness=6.0
risk.sigmoid.midpoint=0.55

# Classification thresholds
risk.thresholds.low=0.35
risk.thresholds.medium=0.65
```

## Running Locally

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`. Uses H2 in-memory database by default.

## Database (PostgreSQL on Render)

Set the following environment variables on Render:

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://host:5432/dbname` |
| `SPRING_DATASOURCE_USERNAME` | your db user |
| `SPRING_DATASOURCE_PASSWORD` | your db password |
| `SPRING_JPA_DATABASE_PLATFORM` | `org.hibernate.dialect.PostgreSQLDialect` |

## Running Tests

```bash
mvn test
```

35 tests across `RiskScoringServiceTest`, `PolicyGenerationServiceTest`, and `QuotationServiceTest`.
