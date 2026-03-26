package com.insurance.underwriting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private String phoneNumber;
    private String phoneExtension;
    private String selectedTier;
    private LocalDateTime submittedAt;

    // Risk result
    private double riskScore;
    private String riskCategory;
    private String recommendedAction;
    private BigDecimal premiumMultiplier;

    // Workflow: PENDING_CUSTOMER_ACCEPTANCE | PENDING_ADMIN_REVIEW | POLICY_ISSUED | REJECTED
    private String workflowStatus;

    // Policy (set when POLICY_ISSUED)
    private String policyNumber;
    private String customerId;
    private BigDecimal annualPremium;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "record_decision_factors", joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "factor")
    @OrderColumn(name = "factor_order")
    @Builder.Default
    private List<String> decisionFactors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "record_coverages", joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "coverage")
    @OrderColumn(name = "coverage_order")
    @Builder.Default
    private List<String> coverages = new ArrayList<>();
}
