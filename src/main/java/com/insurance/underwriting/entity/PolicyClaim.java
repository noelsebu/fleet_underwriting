package com.insurance.underwriting.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "policy_claims")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private String policyNumber;
    private String companyName;
    private String selectedTier;

    private String incidentDescription;
    private BigDecimal claimAmount;
    private LocalDate incidentDate;
    private boolean atFault;

    // PENDING_REVIEW | APPROVED | REJECTED
    private String status;
    private String adminNote;

    private LocalDateTime submittedAt;
}
