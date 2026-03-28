# FleetGuard — Fleet Insurance Management System

A Spring Boot + Thymeleaf web application that automates commercial fleet insurance. Customers get instant risk assessments and quotes; the admin portal handles manual reviews, claim approvals, and premium negotiations.

**Live demo:** [https://fleet-quote.onrender.com](https://fleet-quote.onrender.com)
**Admin portal:** [https://fleet-quote.onrender.com/admin](https://fleet-quote.onrender.com/admin) — credentials: `admin` / `admin123`

---

## Features

| Area | Details |
|---|---|
| **Risk scoring** | Sigmoid-normalised weighted score across 4 dimensions |
| **Plans** | Basic ($10k coverage), Premium ($15k), Diamond ($20k) |
| **Customer workflows** | Instant issue (LOW), manual review request (MEDIUM/HIGH), premium negotiation |
| **Admin portal** | Review queue, claim approvals, premium negotiations, dashboard financials |
| **Claims** | Coverage limit enforcement, admin-set approved amounts, policy auto-expiry |
| **Tracking** | `REQ-` tracking numbers for review requests, `NEG-` for negotiations |
| **Persistence** | H2 in-memory |

---

## Customer Workflow

### 1. Get a Quote
Visit the landing page, choose a plan (Basic / Premium / Diamond), and fill in your fleet profile.

### 2. Risk Assessment
The engine scores your submission and assigns a risk category:

| Score | Category | Next Step |
|---|---|---|
| ≤ 0.35 | **LOW** | Accept instantly → policy issued |
| ≤ 0.65 | **MEDIUM** | Request manual review → tracking number assigned |
| > 0.65 | **HIGH** | Request manual review → tracking number assigned |

### 3. Policy Options (LOW risk)
- **Accept & Generate Policy** — issues your policy immediately
- **Request Lower Premium** — enters negotiation queue; you receive a `NEG-` tracking number

### 4. After Policy Issuance
- You receive a **Customer ID** (e.g. `CUST-2026-0001`) and **Policy Number** (e.g. `Pol-2026-1`)
- Look up your policy at `/policy/lookup`
- File claims at `/claim/new` — coverage tracked per policy, no further claims once the limit is exhausted

### Track a Request
Use your tracking number at `/underwriting/track` to check the status of a review or negotiation request.

---

## Full Workflow Diagram

```
Customer submits quote
        │
        ├── LOW risk ──► PENDING_CUSTOMER_ACCEPTANCE
        │                   │                   │
        │              Accept Policy      Request Lower Premium
        │                   │                   │
        │            POLICY_ISSUED       NEGOTIATION_REQUESTED  (NEG-xxxx assigned)
        │                                       │
        │                            Admin: Send Offer  OR  Accept & Issue
        │                                 │                      │
        │                      NEGOTIATION_OFFERED          POLICY_ISSUED
        │                                 │
        │                        Customer accepts
        │                                 │
        │                           POLICY_ISSUED
        │
        └── MEDIUM / HIGH risk ──► HIGH_RISK_REVIEW
                                        │
                                Customer clicks "Submit Review Request"
                                        │
                                PENDING_ADMIN_REVIEW  (REQ-xxxx assigned)
                                        │
                              Admin approves / rejects
                                 │              │
                          POLICY_ISSUED      REJECTED
```

---

## Admin Portal

Login at `/admin/login` — default credentials: `admin` / `admin123`

| Page | URL | Description |
|---|---|---|
| Dashboard | `/admin/dashboard` | Total submissions, policies issued, revenue, claims paid |
| Review Queue | `/admin/queue` | MEDIUM/HIGH risk applications awaiting decision |
| Negotiations | `/admin/negotiations` | Customers requesting lower premiums |
| All Submissions | `/admin/all` | Full history with workflow status |
| Claims | `/admin/claims` | Pending claims — set approved amount per claim |

### Claims Approval
When approving a claim the admin sets the **approved amount** (independent of what the customer requested). Once cumulative approved claims for a policy reach its coverage limit, the policy is automatically expired.

### Premium Negotiations
Two options per negotiation request:
- **Send Offer** — propose a revised premium; the customer must accept before the policy issues
- **Accept & Issue** — set the premium and issue the policy immediately

---

## Coverage Limits

| Plan | Coverage Limit |
|---|---|
| Basic | $10,000 |
| Premium | $15,000 |
| Diamond | $20,000 |

Once total approved claims reach the coverage limit for a policy, the policy expires and no further claims can be filed.

---

## Risk Scoring

### Pipeline

1. Each of four dimensions produces a raw score (unbounded positive number)
2. Raw scores are summed into a single composite
3. A **sigmoid function** maps the composite smoothly onto (0, 1)
4. The sigmoid output drives category, recommended action, and premium multiplier

```
σ(x) = 1 / (1 + e^(−k · (x − x₀)))

k  = steepness  (how sharply the curve rises — default 6.0)
x₀ = midpoint   (raw score that maps to 0.5 — default 0.35)
```

### Classification

| Sigmoid Score | Category | Recommended Action | Premium Multiplier |
|---|---|---|---|
| ≤ 0.35 | **LOW** | APPROVE | 1.0× – 1.53× |
| ≤ 0.65 | **MEDIUM** | REVIEW | 1.53× – 1.98× |
| > 0.65 | **HIGH** | REJECT | 1.98× – 2.5× |

Multiplier formula: `1.0 + (score × 1.5)` — scales linearly from 1.0× at score=0 to 2.5× at score=1.

### Scoring Weights

#### Business Stability
| Condition | Weight |
|---|---|
| Business < 2 years old | +0.12 |
| Business 2–4 years old | +0.06 |
| Credit score < 600 | +0.08 |
| Credit score 600–699 | +0.04 |

#### Fleet Condition
| Condition | Weight |
|---|---|
| Average vehicle age > 8 years | +0.15 |
| Average vehicle age 4–8 years | +0.07 |
| Primary type: TRUCK | +0.10 |
| Primary type: VAN | +0.07 |
| Primary type: SUV | +0.04 |
| Primary type: SEDAN | 0 (baseline) |

#### Driver Pool
| Condition | Weight |
|---|---|
| Average driver age < 25 | +0.15 |
| Average driver age 25–29 | +0.08 |
| Driver pool > 50 | +0.05 |

#### Claims History
Claims frequency is measured as **claims per vehicle** over the last 3 years.

| Condition | Weight |
|---|---|
| Frequency > 0.5 per vehicle | +0.18 |
| Frequency 0.2–0.5 per vehicle | +0.09 |
| Frequency > 0 but low | +0.03 |
| Average claim severity > $20,000 | +0.08 |
| Average claim severity $8,000–$20,000 | +0.04 |
| At-fault rate > 50% of claims | +0.04 |

### Tuning

All weights, thresholds, and sigmoid parameters are externalized in `application.properties`:

```properties
# Sigmoid
risk.sigmoid.steepness=6.0
risk.sigmoid.midpoint=0.35

# Classification thresholds
risk.thresholds.low=0.35
risk.thresholds.medium=0.65

# Business stability weights
risk.weights.business-new-high=0.12
risk.weights.business-new-moderate=0.06
risk.weights.credit-score-low=0.08
risk.weights.credit-score-fair=0.04

# Fleet weights
risk.weights.fleet-age-high=0.15
risk.weights.fleet-age-moderate=0.07
risk.weights.vehicle-truck=0.10
risk.weights.vehicle-van=0.07
risk.weights.vehicle-suv=0.04

# Driver pool weights
risk.weights.driver-age-high=0.15
risk.weights.driver-age-moderate=0.08
risk.weights.large-driver-pool=0.05

# Claims weights
risk.weights.claims-freq-high=0.18
risk.weights.claims-freq-moderate=0.09
risk.weights.claims-freq-low=0.03
risk.weights.claim-severity-high=0.08
risk.weights.claim-severity-medium=0.04
risk.weights.at-fault-high=0.04
```

---

## Running Locally

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`. Uses H2 in-memory database by default — no setup required.

### Running Tests

```bash
mvn test
```

123 tests across all controllers, services, and security configuration.

---

## Tech Stack

- **Backend** — Spring Boot 2.7, Spring Security, Spring Data JPA
- **Frontend** — Thymeleaf, Bootstrap 5, Bootstrap Icons
- **Database** — H2 (dev) / PostgreSQL (prod)
- **Deployment** — Docker, Render
