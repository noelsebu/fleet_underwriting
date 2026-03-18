package com.insurance.underwriting.service;

import com.insurance.underwriting.config.RiskScoringProperties;
import com.insurance.underwriting.model.RecommendedAction;
import com.insurance.underwriting.model.RiskCategory;
import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.model.VehicleType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk scoring using a sigmoid-normalised weighted sum.
 *
 * Pipeline:
 *   1. Each dimension produces a raw score (unbounded positive number)
 *   2. Raw scores are summed into a single composite
 *   3. Sigmoid maps the composite smoothly onto (0, 1) — no manual clamping
 *   4. The sigmoid output drives category, action, and premium multiplier
 *
 * Sigmoid:  σ(x) = 1 / (1 + e^(−k · (x − x₀)))
 *   k  = steepness  — how sharply the curve rises around the midpoint
 *   x₀ = midpoint   — the raw-score value that maps to 0.5 (MEDIUM boundary)
 *
 * Tuning: raise k to make LOW/HIGH decisions more decisive;
 *         raise x₀ if too many profiles are coming out HIGH.
 */
@Service
public class RiskScoringService {

    private final RiskScoringProperties props;

    public RiskScoringService(RiskScoringProperties props) {
        this.props = props;
    }

    public RiskScoreResponse score(RiskScoreRequest req) {
        List<String> factors = new ArrayList<>();

        // Step 1 — collect raw dimension scores
        double raw = 0.0;
        raw += scoreBusinessStability(req.getBusinessInfo(), factors);
        raw += scoreFleet(req.getFleetDetails(), factors);
        raw += scoreDriverPool(req.getDriverPool(), factors);
        raw += scoreClaims(req.getClaimsHistory(), req.getFleetDetails().getTotalVehicles(), factors);

        // Step 2 — sigmoid normalisation → naturally bounded (0, 1)
        double riskScore = sigmoid(raw);
        riskScore = round(riskScore, 2);

        RiskCategory category     = category(riskScore);
        RecommendedAction action  = action(riskScore);
        BigDecimal mult  = premiumMultiplier(riskScore);

        return RiskScoreResponse.builder()
                .riskScore(riskScore)
                .riskCategory(category)
                .recommendedAction(action)
                .premiumMultiplier(mult)
                .decisionFactors(factors)
                .build();
    }

    // ── Sigmoid ───────────────────────────────────────────────────────────────

    /**
     * σ(x) = 1 / (1 + e^(−k · (x − x₀)))
     * Output is always in (0, 1) regardless of how large x grows.
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-props.getSigmoid().getSteepness() * (x - props.getSigmoid().getMidpoint())));
    }

    // ── Dimension scorers ─────────────────────────────────────────────────────
    // These return raw (unbounded) contribution values.
    // Sigmoid handles the final normalisation — no need to cap at 0.20 etc.

    // Business stability — weight ~0.20 of total raw budget
    private double scoreBusinessStability(RiskScoreRequest.BusinessInfo businessInfo, List<String> factors) {
        double score = 0.0;

        if (businessInfo.getYearsInOperation() < 2) {
            score += props.getWeights().getBusinessNewHigh();
            factors.add("Business under 2 years old — elevated stability risk");
        } else if (businessInfo.getYearsInOperation() < 5) {
            score += props.getWeights().getBusinessNewModerate();
            factors.add("Business under 5 years old — moderate stability risk");
        }

        if (businessInfo.getCreditScore() < 600) {
            score += props.getWeights().getCreditScoreLow();
            factors.add("Low credit score (<600) — elevated financial risk");
        } else if (businessInfo.getCreditScore() < 700) {
            score += props.getWeights().getCreditScoreFair();
            factors.add("Fair credit score (600–699) — moderate financial risk");
        }

        if (businessInfo.isWarZone()) {
            score += props.getWeights().getWarZone();
            factors.add("Operations in war zone — extreme environmental and liability risk");
        }

        return score;
    }

    // Fleet condition — weight ~0.25
    private double scoreFleet(RiskScoreRequest.FleetDetails fleetDetails, List<String> factors) {
        double score = 0.0;

        if (fleetDetails.getAverageVehicleAgeYears() > 8) {
            score += props.getWeights().getFleetAgeHigh();
            factors.add("Fleet average age >8 years — high mechanical risk");
        } else if (fleetDetails.getAverageVehicleAgeYears() > 4) {
            score += props.getWeights().getFleetAgeModerate();
            factors.add("Fleet average age 4–8 years — moderate wear risk");
        }

        switch (fleetDetails.getPrimaryVehicleType()) {
            case TRUCK:
                score += props.getWeights().getVehicleTruck();
                factors.add("Primary type TRUCK — higher liability exposure");
                break;
            case VAN:
                score += props.getWeights().getVehicleVan();
                factors.add("Primary type VAN — moderate liability exposure");
                break;
            case SUV:
                score += props.getWeights().getVehicleSuv();
                factors.add("Primary type SUV — slight elevated exposure");
                break;
            default:
                break; // SEDAN — baseline, no penalty
        }

        return score;
    }

    // Driver pool — weight ~0.20
    private double scoreDriverPool(RiskScoreRequest.DriverPool driverPool, List<String> factors) {
        double score = 0.0;

        if (driverPool.getAverageDriverAge() < 25) {
            score += props.getWeights().getDriverAgeHigh();
            factors.add("Average driver age <25 — high-risk driver pool");
        } else if (driverPool.getAverageDriverAge() < 30) {
            score += props.getWeights().getDriverAgeModerate();
            factors.add("Average driver age 25–29 — moderately young driver pool");
        }

        if (driverPool.getTotalDrivers() > 50) {
            score += props.getWeights().getLargeDriverPool();
            factors.add("Large driver pool (>50) — variable driver quality risk");
        }

        return score;
    }

    // Claims history — weight ~0.30 (strongest signal)
    private double scoreClaims(RiskScoreRequest.ClaimsHistory claimsHistory, int totalVehicles, List<String> factors) {
        double score = 0.0;

        double claimsPerVehicle = totalVehicles > 0
                ? (double) claimsHistory.getClaimsLast3Years() / totalVehicles
                : claimsHistory.getClaimsLast3Years();

        if (claimsPerVehicle > 0.5) {
            score += props.getWeights().getClaimsFreqHigh();
            factors.add("High claims frequency (>0.5 per vehicle) — significant loss history");
        } else if (claimsPerVehicle > 0.2) {
            score += props.getWeights().getClaimsFreqModerate();
            factors.add("Moderate claims frequency (0.2–0.5 per vehicle)");
        } else if (claimsHistory.getClaimsLast3Years() > 0) {
            score += props.getWeights().getClaimsFreqLow();
            factors.add("Low claims frequency — minor loss history present");
        }

        if (claimsHistory.getClaimsLast3Years() > 0) {
            double avgClaim = claimsHistory.getTotalClaimAmount().doubleValue() / claimsHistory.getClaimsLast3Years();
            if (avgClaim > 20000) {
                score += props.getWeights().getClaimSeverityHigh();
                factors.add("High average claim severity (>$20,000)");
            } else if (avgClaim > 8000) {
                score += props.getWeights().getClaimSeverityMedium();
                factors.add("Moderate average claim severity ($8,000–$20,000)");
            }

            double atFaultRate = (double) claimsHistory.getAtFaultCount() / claimsHistory.getClaimsLast3Years();
            if (atFaultRate > 0.5) {
                score += props.getWeights().getAtFaultHigh();
                factors.add("High at-fault rate (>50% of claims) — driver behaviour concern");
            }
        }

        return score;
    }

    // ── Classification ────────────────────────────────────────────────────────

    private RiskCategory category(double score) {
        if (score <= props.getThresholds().getLow()) return RiskCategory.LOW;
        if (score <= props.getThresholds().getMedium()) return RiskCategory.MEDIUM;
        return RiskCategory.HIGH;
    }

    private RecommendedAction action(double score) {
        if (score <= props.getThresholds().getLow()) return RecommendedAction.APPROVE;
        if (score <= props.getThresholds().getMedium()) return RecommendedAction.REVIEW;
        return RecommendedAction.REJECT;
    }

    // Premium scales from 1.0× (score=0) to 2.5× (score=1)
    private BigDecimal premiumMultiplier(double score) {
        double multiplier = 1.0 + (score * 1.5);
        return BigDecimal.valueOf(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double val, int places) {
        return BigDecimal.valueOf(val).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
