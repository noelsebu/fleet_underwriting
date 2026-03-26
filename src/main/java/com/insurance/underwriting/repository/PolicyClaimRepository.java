package com.insurance.underwriting.repository;

import com.insurance.underwriting.entity.PolicyClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyClaimRepository extends JpaRepository<PolicyClaim, Long> {
    List<PolicyClaim> findByStatusOrderBySubmittedAtDesc(String status);
    List<PolicyClaim> findAllByOrderBySubmittedAtDesc();
    List<PolicyClaim> findByCustomerIdOrPolicyNumber(String customerId, String policyNumber);
    long countByStatus(String status);
}
