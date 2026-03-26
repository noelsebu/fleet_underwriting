package com.insurance.underwriting;

import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.model.RecommendedAction;
import com.insurance.underwriting.model.RiskCategory;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.service.QuotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuotationServiceTest {

    private QuotationService service;

    @BeforeEach
    void setUp() {
        service = new QuotationService();
    }

    private RiskScoreResponse responseWithMultiplier(double multiplier) {
        return RiskScoreResponse.builder()
                .riskScore(0.2)
                .riskCategory(RiskCategory.LOW)
                .recommendedAction(RecommendedAction.APPROVE)
                .premiumMultiplier(BigDecimal.valueOf(multiplier))
                .decisionFactors(List.of())
                .build();
    }

    @Test
    void generateQuotes_shouldReturnThreeTiers() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.0));
        assertThat(quotes).hasSize(3);
        assertThat(quotes).extracting(PolicyQuote::getTier)
                .containsExactly("Basic", "Premium", "Diamond");
    }

    @Test
    void basicQuote_shouldHaveOnlyCoverageAccident() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.0));
        PolicyQuote basic = quotes.get(0);
        assertThat(basic.getCoverages()).containsExactly("Accident coverage");
    }

    @Test
    void premiumQuote_shouldHaveThreeCoverages() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.0));
        PolicyQuote premium = quotes.get(1);
        assertThat(premium.getCoverages()).containsExactlyInAnyOrder(
                "Accident coverage", "Bumper-to-bumper", "3rd party insurance"
        );
    }

    @Test
    void diamondQuote_shouldHaveAllFourCoverages() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.0));
        PolicyQuote diamond = quotes.get(2);
        assertThat(diamond.getCoverages()).containsExactlyInAnyOrder(
                "Accident coverage", "Bumper-to-bumper", "3rd party insurance", "Driver health coverage"
        );
    }

    @Test
    void quotedPremium_shouldEqualBasePriceTimesMultiplier() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.5));
        // Basic: 1000 * 1.5 = 1500.00
        assertThat(quotes.get(0).getQuotedPremium()).isEqualByComparingTo(new BigDecimal("1500.00"));
        // Premium: 1500 * 1.5 = 2250.00
        assertThat(quotes.get(1).getQuotedPremium()).isEqualByComparingTo(new BigDecimal("2250.00"));
        // Diamond: 2000 * 1.5 = 3000.00
        assertThat(quotes.get(2).getQuotedPremium()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    void highRiskMultiplier_shouldProduceLargerPremiumsThanLowRisk() {
        List<PolicyQuote> lowRisk  = service.generateQuotes(responseWithMultiplier(1.2));
        List<PolicyQuote> highRisk = service.generateQuotes(responseWithMultiplier(2.2));
        for (int i = 0; i < 3; i++) {
            assertThat(highRisk.get(i).getQuotedPremium())
                    .isGreaterThan(lowRisk.get(i).getQuotedPremium());
        }
    }

    @Test
    void basePrices_shouldBeCorrectPerTier() {
        List<PolicyQuote> quotes = service.generateQuotes(responseWithMultiplier(1.0));
        assertThat(quotes.get(0).getBasePrice()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(quotes.get(1).getBasePrice()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(quotes.get(2).getBasePrice()).isEqualByComparingTo(new BigDecimal("2000"));
    }
}
