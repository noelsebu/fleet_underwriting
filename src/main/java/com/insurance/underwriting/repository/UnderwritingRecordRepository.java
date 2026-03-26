package com.insurance.underwriting.repository;

import com.insurance.underwriting.entity.UnderwritingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface UnderwritingRecordRepository extends JpaRepository<UnderwritingRecord, Long> {
    List<UnderwritingRecord> findAllByOrderBySubmittedAtDesc();
    List<UnderwritingRecord> findByWorkflowStatusOrderBySubmittedAtDesc(String workflowStatus);
    List<UnderwritingRecord> findByCustomerIdAndWorkflowStatus(String customerId, String workflowStatus);
    java.util.Optional<UnderwritingRecord> findByTrackingNumber(String trackingNumber);

    @Query("SELECT COALESCE(SUM(r.annualPremium), 0) FROM UnderwritingRecord r WHERE r.workflowStatus = 'POLICY_ISSUED'")
    BigDecimal sumAnnualPremiumForIssuedPolicies();

    java.util.Optional<UnderwritingRecord> findByPolicyNumber(String policyNumber);
}
