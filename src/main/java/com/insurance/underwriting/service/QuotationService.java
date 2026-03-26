package com.insurance.underwriting.service;

import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.model.RiskScoreResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
public class QuotationService {

    public List<PolicyQuote> generateQuotes(RiskScoreResponse risk) {
        BigDecimal multiplier = risk.getPremiumMultiplier();

        return Arrays.asList(
            PolicyQuote.builder()
                .tier("Basic")
                .basePrice(new BigDecimal("1000"))
                .quotedPremium(new BigDecimal("1000").multiply(multiplier).setScale(2, RoundingMode.HALF_UP))
                .coverages(Arrays.asList("Accident coverage"))
                .build(),

            PolicyQuote.builder()
                .tier("Premium")
                .basePrice(new BigDecimal("1500"))
                .quotedPremium(new BigDecimal("1500").multiply(multiplier).setScale(2, RoundingMode.HALF_UP))
                .coverages(Arrays.asList("Accident coverage", "Bumper-to-bumper", "3rd party insurance"))
                .build(),

            PolicyQuote.builder()
                .tier("Diamond")
                .basePrice(new BigDecimal("2000"))
                .quotedPremium(new BigDecimal("2000").multiply(multiplier).setScale(2, RoundingMode.HALF_UP))
                .coverages(Arrays.asList("Accident coverage", "Bumper-to-bumper", "3rd party insurance", "Driver health coverage"))
                .build()
        );
    }
}
