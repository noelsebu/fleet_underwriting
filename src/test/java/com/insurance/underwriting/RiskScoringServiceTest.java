package com.insurance.underwriting;

import com.insurance.underwriting.config.RiskScoringProperties;
import com.insurance.underwriting.model.RecommendedAction;
import com.insurance.underwriting.model.RiskCategory;
import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.model.VehicleType;
import com.insurance.underwriting.service.RiskScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringServiceTest {

    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        service = new RiskScoringService(new RiskScoringProperties());
    }

    @Test
    void lowRiskProfile_shouldApprove() {
        RiskScoreResponse r = service.score(buildRequest(
                10, 750,          // stable business, good credit
                20, 3.0, VehicleType.SEDAN, // newer fleet, sedans
                35.0, 10,         // mature drivers
                1, new BigDecimal("3000"), 0  // minimal claims
        ));
        assertThat(r.getRiskCategory()).isEqualTo(RiskCategory.LOW);
        assertThat(r.getRecommendedAction()).isEqualTo(RecommendedAction.APPROVE);
        // Sigmoid output: low raw score → well below midpoint → < 0.35
        assertThat(r.getRiskScore()).isLessThanOrEqualTo(0.35);
    }

    @Test
    void highRiskProfile_shouldReject() {
        RiskScoreResponse r = service.score(buildRequest(
                1, 520,            // new business, bad credit
                30, 10.0, VehicleType.TRUCK, // old truck fleet
                23.0, 60,          // young large driver pool
                15, new BigDecimal("300000"), 10 // heavy claims, at-fault
        ));
        assertThat(r.getRiskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(r.getRecommendedAction()).isEqualTo(RecommendedAction.REJECT);
        // Sigmoid output: large raw score → far above midpoint → > 0.65
        assertThat(r.getRiskScore()).isGreaterThan(0.65);
    }

    @Test
    void sigmoidOutput_isAlwaysStrictlyBetweenZeroAndOne() {
        // Extreme worst-case inputs — sigmoid must still stay within (0, 1)
        RiskScoreResponse r = service.score(buildRequest(
                0, 300, 100, 20.0, VehicleType.TRUCK, 18.0, 200, 100, new BigDecimal("5000000"), 90
        ));
        assertThat(r.getRiskScore()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void cleanProfile_noFactors_producesLowScore() {
        // Perfect profile — no risk flags at all
        RiskScoreResponse r = service.score(buildRequest(
                20, 800, 10, 2.0, VehicleType.SEDAN, 40.0, 8, 0, new BigDecimal("0"), 0
        ));
        // With zero raw score, sigmoid(0 - 0.55) ≈ 0.13 — comfortably LOW
        assertThat(r.getRiskScore()).isLessThan(0.35);
        assertThat(r.getDecisionFactors()).isEmpty();
    }

    @Test
    void decisionFactors_arePopulated_forRiskyProfile() {
        RiskScoreResponse r = service.score(buildRequest(
                1, 500, 10, 9.0, VehicleType.VAN, 24.0, 5, 4, new BigDecimal("80000"), 3
        ));
        assertThat(r.getDecisionFactors()).isNotEmpty();
    }

    @Test
    void premiumMultiplier_increasesWithRiskScore() {
        RiskScoreResponse low = service.score(buildRequest(
                15, 780, 10, 2.0, VehicleType.SEDAN, 38.0, 8, 0, new BigDecimal("0"), 0
        ));
        RiskScoreResponse high = service.score(buildRequest(
                1, 520, 30, 10.0, VehicleType.TRUCK, 23.0, 60, 15, new BigDecimal("300000"), 10
        ));
        assertThat(high.getPremiumMultiplier()).isGreaterThan(low.getPremiumMultiplier());
    }

    // ── Additional cases ──────────────────────────────────────────────────────

    @Test
    void mediumRiskProfile_shouldReview() {
        // Raw breakdown: business(3yr=0.06, credit650=0.04) + fleet(5yr=0.07, TRUCK=0.10)
        //   + driver(age28=0.08) + claims(4/10=0.40→moderate=0.09, avg$10k→moderate=0.04)
        //   = 0.48  →  sigmoid(0.48−0.55) ≈ 0.40  →  MEDIUM
        RiskScoreResponse r = service.score(buildRequest(
                3, 650,                          // young business, fair credit
                10, 5.0, VehicleType.TRUCK,      // moderate fleet age, trucks
                28.0, 15,                        // moderately young drivers
                4, new BigDecimal("40000"), 1    // 4/10 = 0.40 per vehicle → moderate claims
        ));
        assertThat(r.getRiskCategory()).isEqualTo(RiskCategory.MEDIUM);
        assertThat(r.getRecommendedAction()).isEqualTo(RecommendedAction.REVIEW);
        assertThat(r.getRiskScore()).isGreaterThan(0.35).isLessThanOrEqualTo(0.65);
    }

    @Test
    void truckFleet_shouldAddHigherPenaltyThanVan() {
        RiskScoreResponse van = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.VAN, 35.0, 10, 0, new BigDecimal("0"), 0
        ));
        RiskScoreResponse truck = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.TRUCK, 35.0, 10, 0, new BigDecimal("0"), 0
        ));
        assertThat(truck.getRiskScore()).isGreaterThan(van.getRiskScore());
    }

    @Test
    void suvFleet_shouldAddSomeRiskOverSedan() {
        RiskScoreResponse sedan = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10, 0, new BigDecimal("0"), 0
        ));
        RiskScoreResponse suv = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SUV, 35.0, 10, 0, new BigDecimal("0"), 0
        ));
        // SUV weight (0.04) is too small to survive 2-decimal rounding from the baseline;
        // assert on the decision factor instead which is the reliable signal.
        assertThat(suv.getDecisionFactors()).anyMatch(f -> f.contains("SUV"));
        assertThat(sedan.getDecisionFactors()).noneMatch(f -> f.contains("SUV"));
    }

    @Test
    void youngDriverPool_25to29_shouldAddModerateRisk() {
        RiskScoreResponse mature = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 40.0, 10, 0, new BigDecimal("0"), 0
        ));
        RiskScoreResponse young = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 27.0, 10, 0, new BigDecimal("0"), 0
        ));
        assertThat(young.getRiskScore()).isGreaterThan(mature.getRiskScore());
        assertThat(young.getDecisionFactors()).anyMatch(f -> f.contains("25–29"));
    }

    @Test
    void largeDriverPool_shouldAddRisk() {
        RiskScoreResponse small = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10, 0, new BigDecimal("0"), 0
        ));
        RiskScoreResponse large = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 60, 0, new BigDecimal("0"), 0
        ));
        assertThat(large.getRiskScore()).isGreaterThan(small.getRiskScore());
        assertThat(large.getDecisionFactors()).anyMatch(f -> f.contains("Large driver pool"));
    }

    @Test
    void moderateClaimsFrequency_shouldAddRisk() {
        // 5 claims / 15 vehicles = 0.33 → moderate band (0.2–0.5)
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 15, 3.0, VehicleType.SEDAN, 35.0, 10,
                5, new BigDecimal("20000"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("Moderate claims frequency"));
    }

    @Test
    void highAtFaultRate_shouldAddRisk() {
        // 4 claims, 3 at-fault → 75% at-fault rate
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 15, 3.0, VehicleType.SEDAN, 35.0, 10,
                4, new BigDecimal("40000"), 3
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("at-fault rate"));
    }

    @Test
    void highAverageClaimSeverity_shouldFlagFactor() {
        // 1 claim of $25,000 → avg > $20,000
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10,
                1, new BigDecimal("25000"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("High average claim severity"));
    }

    @Test
    void moderateClaimSeverity_shouldFlagFactor() {
        // 1 claim of $12,000 → avg in $8,000–$20,000
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10,
                1, new BigDecimal("12000"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("Moderate average claim severity"));
    }

    @Test
    void oldFleet_shouldFlagHighMechanicalRisk() {
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 10, 9.0, VehicleType.SEDAN, 35.0, 10,
                0, new BigDecimal("0"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains(">8 years"));
    }

    @Test
    void premiumMultiplier_isWithinExpectedRange() {
        RiskScoreResponse r = service.score(buildRequest(
                10, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10,
                0, new BigDecimal("0"), 0
        ));
        assertThat(r.getPremiumMultiplier()).isBetween(new BigDecimal("1.00"), new BigDecimal("2.50"));
    }

    @Test
    void newBusiness_under2Years_shouldFlagElevatedRisk() {
        RiskScoreResponse r = service.score(buildRequest(
                1, 750, 10, 3.0, VehicleType.SEDAN, 35.0, 10,
                0, new BigDecimal("0"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("under 2 years"));
    }

    @Test
    void lowCreditScore_shouldFlagFinancialRisk() {
        RiskScoreResponse r = service.score(buildRequest(
                10, 550, 10, 3.0, VehicleType.SEDAN, 35.0, 10,
                0, new BigDecimal("0"), 0
        ));
        assertThat(r.getDecisionFactors()).anyMatch(f -> f.contains("Low credit score"));
    }

    @Test
    void warZone_shouldSignificantlyIncreaseScore() {
        RiskScoreResponse noWarZone = service.score(buildRequest(
                10, 750, 20, 3.0, VehicleType.SEDAN, 35.0, 10, 0, new BigDecimal("0"), 0, false
        ));
        RiskScoreResponse warZone = service.score(buildRequest(
                10, 750, 20, 3.0, VehicleType.SEDAN, 35.0, 10, 0, new BigDecimal("0"), 0, true
        ));
        assertThat(warZone.getRiskScore()).isGreaterThan(noWarZone.getRiskScore());
        assertThat(warZone.getDecisionFactors()).anyMatch(f -> f.contains("war zone"));
    }

    private RiskScoreRequest buildRequest(
            int years, int credit,
            int vehicles, double fleetAge, VehicleType vehicleType,
            double driverAge, int drivers,
            int claims, BigDecimal claimAmount, int atFault) {
        return buildRequest(years, credit, vehicles, fleetAge, vehicleType, driverAge, drivers, claims, claimAmount, atFault, false);
    }

    private RiskScoreRequest buildRequest(
            int years, int credit,
            int vehicles, double fleetAge, VehicleType vehicleType,
            double driverAge, int drivers,
            int claims, BigDecimal claimAmount, int atFault, boolean warZone) {

        RiskScoreRequest req = new RiskScoreRequest();

        RiskScoreRequest.BusinessInfo b = new RiskScoreRequest.BusinessInfo();
        b.setCompanyName("Test Co");
        b.setYearsInOperation(years);
        b.setCreditScore(credit);
        b.setWarZone(warZone);
        req.setBusinessInfo(b);

        RiskScoreRequest.FleetDetails f = new RiskScoreRequest.FleetDetails();
        f.setTotalVehicles(vehicles);
        f.setAverageVehicleAgeYears(fleetAge);
        f.setPrimaryVehicleType(vehicleType);
        req.setFleetDetails(f);

        RiskScoreRequest.DriverPool d = new RiskScoreRequest.DriverPool();
        d.setTotalDrivers(drivers);
        d.setAverageDriverAge(driverAge);
        req.setDriverPool(d);

        RiskScoreRequest.ClaimsHistory c = new RiskScoreRequest.ClaimsHistory();
        c.setClaimsLast3Years(claims);
        c.setTotalClaimAmount(claimAmount);
        c.setAtFaultCount(atFault);
        req.setClaimsHistory(c);

        return req;
    }
}
