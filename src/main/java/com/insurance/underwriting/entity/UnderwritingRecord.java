package com.insurance.underwriting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "underwriting_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyName;
    private String selectedTier;
    private LocalDateTime submittedAt;

    // Risk result
    private double riskScore;
    private String riskCategory;
    private String recommendedAction;
    private BigDecimal premiumMultiplier;

    // Policy (null if HIGH risk)
    private String policyNumber;
    private String policyStatus;
    private BigDecimal annualPremium;
}
