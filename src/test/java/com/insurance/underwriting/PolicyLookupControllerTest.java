package com.insurance.underwriting;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolicyLookupControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UnderwritingRecordRepository repo;
    @Autowired PolicyClaimRepository claimRepo;

    // ── GET /policy/lookup ────────────────────────────────────────────────────

    @Test
    void lookupForm_isPubliclyAccessible() throws Exception {
        mvc.perform(get("/policy/lookup"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-lookup"));
    }

    // ── GET /policy/check ─────────────────────────────────────────────────────

    @Test
    void checkPolicy_byCustomerId_returnsDetail() throws Exception {
        repo.save(issuedRecord("CUST-2025-0010", "Pol-2025-10"));

        mvc.perform(get("/policy/check").param("reference", "CUST-2025-0010"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-detail"))
           .andExpect(model().attributeExists("record"));
    }

    @Test
    void checkPolicy_byPolicyNumber_returnsDetail() throws Exception {
        repo.save(issuedRecord("CUST-2025-0011", "Pol-2025-11"));

        mvc.perform(get("/policy/check").param("reference", "Pol-2025-11"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-detail"))
           .andExpect(model().attributeExists("record"));
    }

    @Test
    void checkPolicy_caseInsensitive_matchesByCustomerId() throws Exception {
        repo.save(issuedRecord("CUST-2025-0012", "Pol-2025-12"));

        mvc.perform(get("/policy/check").param("reference", "cust-2025-0012"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-detail"));
    }

    @Test
    void checkPolicy_caseInsensitive_matchesByPolicyNumber() throws Exception {
        repo.save(issuedRecord("CUST-2025-0013", "Pol-2025-13"));

        mvc.perform(get("/policy/check").param("reference", "pol-2025-13"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-detail"));
    }

    @Test
    void checkPolicy_unknownReference_showsLookupFormWithError() throws Exception {
        mvc.perform(get("/policy/check").param("reference", "INVALID-9999"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-lookup"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    void checkPolicy_notIssuedRecord_notFoundViaLookup() throws Exception {
        // A record that exists but doesn't have a customerId set (PENDING_ADMIN_REVIEW)
        repo.save(UnderwritingRecord.builder()
                .companyName("PendingCo")
                .phoneNumber("5550000000")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.55)
                .riskCategory("MEDIUM")
                .recommendedAction("REVIEW")
                .premiumMultiplier(new BigDecimal("1.25"))
                .annualPremium(new BigDecimal("5500"))
                .workflowStatus("PENDING_ADMIN_REVIEW")
                .build());

        mvc.perform(get("/policy/check").param("reference", "PendingCo"))
           .andExpect(view().name("policy-lookup"))
           .andExpect(model().attributeExists("error"));
    }

    // ── Claims history on policy detail ──────────────────────────────────────

    @Test
    void checkPolicy_includesClaimsInModel() throws Exception {
        repo.save(issuedRecord("CUST-2025-0020", "Pol-2025-20"));
        claimRepo.save(PolicyClaim.builder()
                .customerId("CUST-2025-0020")
                .policyNumber("Pol-2025-20")
                .companyName("LookupFleet")
                .selectedTier("Basic")
                .incidentDescription("Minor scrape.")
                .claimAmount(new BigDecimal("800"))
                .incidentDate(LocalDate.now())
                .atFault(false)
                .status("APPROVED")
                .submittedAt(LocalDateTime.now())
                .build());

        mvc.perform(get("/policy/check").param("reference", "CUST-2025-0020"))
           .andExpect(status().isOk())
           .andExpect(view().name("policy-detail"))
           .andExpect(model().attributeExists("claims"));
    }

    @Test
    void checkPolicy_noClaims_returnsEmptyClaimsList() throws Exception {
        repo.save(issuedRecord("CUST-2025-0021", "Pol-2025-21"));

        mvc.perform(get("/policy/check").param("reference", "Pol-2025-21"))
           .andExpect(status().isOk())
           .andExpect(model().attribute("claims", org.hamcrest.Matchers.hasSize(0)));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private UnderwritingRecord issuedRecord(String customerId, String policyNumber) {
        return UnderwritingRecord.builder()
                .companyName("LookupFleet")
                .phoneNumber("5550000000")
                .selectedTier("Premium")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.18)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new BigDecimal("1.05"))
                .annualPremium(new BigDecimal("8200"))
                .workflowStatus("POLICY_ISSUED")
                .customerId(customerId)
                .policyNumber(policyNumber)
                .build();
    }
}
