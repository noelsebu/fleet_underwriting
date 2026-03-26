# FleetGuard — Fleet Underwriting Risk Scoring

A Spring Boot application with a Thymeleaf web UI that automates insurance underwriting decisions for commercial vehicle fleets. Submit your fleet profile through the web form, get a risk-adjusted quote instantly, and receive a generated policy document across Basic, Premium, and Diamond tiers.

## Features

- Web UI built with Thymeleaf and Bootstrap 5
- Landing page with plan selection (Basic, Premium, Diamond)
- Underwriting form covering business info, fleet details, driver pool, and claims history
- Risk scoring engine using a sigmoid-normalised weighted sum
- Instant policy generation with a unique policy number

## How It Works

### Web Flow

1. **Choose a Plan** — Select Basic, Premium, or Diamond coverage on the landing page
2. **Fill In Your Details** — Enter business, fleet, driver, and claims information
3. **Get Your Policy** — Receive your risk-adjusted quote and policy number instantly

### Scoring Dimensions

Each submission is scored across four dimensions:

| Dimension | Factors |
|---|---|
| Business Stability | Company age, credit score, war zone operations |
| Fleet Condition | Average vehicle age, primary vehicle type |
| Driver Pool | Average driver age, pool size |
| Claims History | Frequency, severity, at-fault rate |

Dimension scores are summed and passed through a **sigmoid function** to produce a normalized score between 0 and 1:

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

All scoring weights and thresholds are externalized in `application.properties` and can be overridden per environment without code changes.

```properties
# Sigmoid tuning
risk.sigmoid.steepness=6.0
risk.sigmoid.midpoint=0.55

# Classification thresholds
risk.thresholds.low=0.35
risk.thresholds.medium=0.65

# Weights (example)
risk.weights.war-zone=0.20
risk.weights.vehicle-truck=0.10
```

## Running Locally

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`.

## Running Tests

```bash
mvn test
```
