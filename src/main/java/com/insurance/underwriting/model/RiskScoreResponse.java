package com.insurance.underwriting.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RiskScoreResponse {

    private double riskScore;              // 0.0 – 1.0
    private RiskCategory riskCategory;
    private RecommendedAction recommendedAction;
    private BigDecimal premiumMultiplier;
    private List<String> decisionFactors;
}
