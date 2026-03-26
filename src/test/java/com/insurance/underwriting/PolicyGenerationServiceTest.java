package com.insurance.underwriting;

import com.insurance.underwriting.model.GeneratedPolicy;
import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.service.PolicyGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyGenerationServiceTest {

    private PolicyGenerationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyGenerationService();
    }

    private PolicyQuote basicQuote() {
        return PolicyQuote.builder()
                .tier("Basic")
                .basePrice(new BigDecimal("1000"))
                .quotedPremium(new BigDecimal("1200.00"))
                .coverages(List.of("Accident coverage"))
                .build();
    }

    @Test
    void highRisk_shouldNotGeneratePolicy() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "HIGH");
        assertThat(result).isEmpty();
    }

    @Test
    void lowRisk_shouldGenerateActivePolicy() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "LOW");
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void mediumRisk_shouldGeneratePendingReviewPolicy() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "MEDIUM");
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void generatedPolicy_shouldHaveCorrectPolicyNumberFormat() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "LOW");
        assertThat(result).isPresent();
        String policyNumber = result.get().getPolicyNumber();
        int year = LocalDate.now().getYear();
        assertThat(policyNumber).matches("Pol-" + year + "-\\d+");
    }

    @Test
    void generatedPolicy_shouldCarryTierAndPremiumFromQuote() {
        PolicyQuote quote = basicQuote();
        Optional<GeneratedPolicy> result = service.generate(quote, "LOW");
        assertThat(result).isPresent();
        GeneratedPolicy policy = result.get();
        assertThat(policy.getTier()).isEqualTo("Basic");
        assertThat(policy.getAnnualPremium()).isEqualByComparingTo(new BigDecimal("1200.00"));
    }

    @Test
    void generatedPolicy_shouldHaveTodayAsIssueDate() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "LOW");
        assertThat(result).isPresent();
        assertThat(result.get().getIssueDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void policyNumbers_shouldBeUnique_forConsecutiveCalls() {
        Optional<GeneratedPolicy> first  = service.generate(basicQuote(), "LOW");
        Optional<GeneratedPolicy> second = service.generate(basicQuote(), "LOW");
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getPolicyNumber()).isNotEqualTo(second.get().getPolicyNumber());
    }

    @Test
    void generatedPolicy_shouldIncludeCoveragesFromQuote() {
        Optional<GeneratedPolicy> result = service.generate(basicQuote(), "LOW");
        assertThat(result).isPresent();
        assertThat(result.get().getCoverages()).containsExactly("Accident coverage");
    }
}
