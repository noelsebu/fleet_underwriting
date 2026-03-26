package com.insurance.underwriting.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class GeneratedPolicy {
    private String policyNumber;
    private String tier;
    private BigDecimal annualPremium;
    private List<String> coverages;
    private LocalDate issueDate;
    private String status; // ACTIVE, PENDING_REVIEW
}
