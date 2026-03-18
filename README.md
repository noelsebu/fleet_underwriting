# Fleet Underwriting Risk Scoring API

A Spring Boot REST API that automates insurance underwriting decisions for commercial vehicle fleets. Accepts a fleet profile and returns a risk score, recommended action, and premium multiplier.

## How It Works

Each request is scored across four dimensions:

| Dimension | Factors |
|---|---|
| Business Stability | Company age, credit score, war zone operations |
| Fleet Condition | Average vehicle age, primary vehicle type |
| Driver Pool | Average driver age, pool size |
| Claims History | Frequency, severity, at-fault rate |

Dimension scores are summed and passed through a **sigmoid function** to produce a normalized score between 0 and 1, which maps to a final decision:

| Score | Category | Action | Premium Multiplier |
|---|---|---|---|
| ≤ 0.35 | LOW | APPROVE | 1.0× – 1.5× |
| ≤ 0.65 | MEDIUM | REVIEW | 1.5× – 2.0× |
| > 0.65 | HIGH | REJECT | 2.0× – 2.5× |

## Endpoints

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
