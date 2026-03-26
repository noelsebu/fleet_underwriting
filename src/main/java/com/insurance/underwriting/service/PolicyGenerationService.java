package com.insurance.underwriting.service;

import com.insurance.underwriting.model.GeneratedPolicy;
import com.insurance.underwriting.model.PolicyQuote;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PolicyGenerationService {

    private final AtomicInteger counter = new AtomicInteger(1000);

    public Optional<GeneratedPolicy> generate(PolicyQuote quote, String riskCategory) {
        if ("HIGH".equals(riskCategory)) {
            return Optional.empty();
        }

        int customerId = counter.getAndIncrement();
        int year = LocalDate.now().getYear();
        String policyNumber = String.format("Pol-%d-%d", year, customerId);
        String status = "MEDIUM".equals(riskCategory) ? "PENDING_REVIEW" : "ACTIVE";

        return Optional.of(GeneratedPolicy.builder()
                .policyNumber(policyNumber)
                .tier(quote.getTier())
                .annualPremium(quote.getQuotedPremium())
                .coverages(quote.getCoverages())
                .issueDate(LocalDate.now())
                .status(status)
                .build());
    }
}
