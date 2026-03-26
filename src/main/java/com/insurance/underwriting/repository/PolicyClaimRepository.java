package com.insurance.underwriting.repository;

import com.insurance.underwriting.entity.PolicyClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PolicyClaimRepository extends JpaRepository<PolicyClaim, Long> {
    List<PolicyClaim> findByStatusOrderBySubmittedAtDesc(String status);
    List<PolicyClaim> findAllByOrderBySubmittedAtDesc();
    List<PolicyClaim> findByCustomerIdOrPolicyNumber(String customerId, String policyNumber);
    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM PolicyClaim c WHERE c.policyNumber = :policyNumber AND c.status = 'APPROVED'")
    BigDecimal sumApprovedAmountByPolicyNumber(@Param("policyNumber") String policyNumber);

    @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM PolicyClaim c WHERE c.status = 'APPROVED'")
    BigDecimal sumAllApprovedClaimAmounts();
}
