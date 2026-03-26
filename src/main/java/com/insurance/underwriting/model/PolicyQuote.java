package com.insurance.underwriting.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PolicyQuote {
    private String tier;
    private BigDecimal basePrice;
    private BigDecimal quotedPremium;
    private List<String> coverages;
}
