package com.insurance.underwriting;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClaimControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UnderwritingRecordRepository recordRepo;
    @Autowired PolicyClaimRepository claimRepo;

    // ── GET /claim/new ────────────────────────────────────────────────────────

    @Test
    void claimForm_isPubliclyAccessible() throws Exception {
        mvc.perform(get("/claim/new"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"));
    }

    // ── POST /claim/submit — valid policy ────────────────────────────────────

    @Test
    void submitClaim_byCustomerId_createsClaimAndShowsConfirmation() throws Exception {
        UnderwritingRecord policy = issuedPolicy("CUST-2025-0099", "Pol-2025-99");

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "CUST-2025-0099")
           .param("incidentDescription", "Rear-end collision on highway.")
           .param("claimAmount", "8500.00")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-submitted"))
           .andExpect(model().attributeExists("claim"));

        List<PolicyClaim> saved = claimRepo.findByCustomerIdOrPolicyNumber("CUST-2025-0099", "Pol-2025-99");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(saved.get(0).getClaimAmount()).isEqualByComparingTo(new BigDecimal("8500.00"));
    }

    @Test
    void submitClaim_byPolicyNumber_createsClaimSuccessfully() throws Exception {
        issuedPolicy("CUST-2025-0100", "Pol-2025-100");

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-100")
           .param("incidentDescription", "Vehicle theft at warehouse.")
           .param("claimAmount", "15000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-submitted"));

        assertThat(claimRepo.findByCustomerIdOrPolicyNumber("CUST-2025-0100", "Pol-2025-100")).hasSize(1);
    }

    @Test
    void submitClaim_caseInsensitiveReference_matchesPolicy() throws Exception {
        issuedPolicy("CUST-2025-0101", "Pol-2025-101");

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "cust-2025-0101")   // lowercase
           .param("incidentDescription", "Windscreen damage.")
           .param("claimAmount", "1200")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-submitted"));
    }

    @Test
    void submitClaim_atFaultTrue_persistsAtFaultFlag() throws Exception {
        issuedPolicy("CUST-2025-0102", "Pol-2025-102");

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-102")
           .param("incidentDescription", "Driver ran red light.")
           .param("claimAmount", "4000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "true"))
           .andExpect(view().name("claim-submitted"));

        PolicyClaim claim = claimRepo.findByCustomerIdOrPolicyNumber("CUST-2025-0102", "Pol-2025-102").get(0);
        assertThat(claim.isAtFault()).isTrue();
    }

    // ── POST /claim/submit — invalid reference ────────────────────────────────

    @Test
    void submitClaim_unknownReference_returnsFormWithError() throws Exception {
        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "UNKNOWN-9999")
           .param("incidentDescription", "Some incident.")
           .param("claimAmount", "500")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    void submitClaim_policyNotIssued_returnsFormWithError() throws Exception {
        // Record exists but is still in PENDING_ADMIN_REVIEW — no active policy yet
        recordRepo.save(UnderwritingRecord.builder()
                .companyName("PendingFleet")
                .phoneNumber("5550000000")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.55)
                .riskCategory("MEDIUM")
                .recommendedAction("REVIEW")
                .premiumMultiplier(new BigDecimal("1.25"))
                .annualPremium(new BigDecimal("5500"))
                .workflowStatus("PENDING_ADMIN_REVIEW")
                .customerId("CUST-2025-0200")
                .policyNumber("Pol-2025-200")
                .build());

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "CUST-2025-0200")
           .param("incidentDescription", "Incident.")
           .param("claimAmount", "1000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    void submitClaim_populatesAllFieldsOnConfirmationPage() throws Exception {
        issuedPolicy("CUST-2025-0103", "Pol-2025-103");

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "CUST-2025-0103")
           .param("incidentDescription", "Flood damage to 3 vehicles.")
           .param("claimAmount", "22000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(model().attribute("claim",
                   org.hamcrest.Matchers.hasProperty("claimAmount",
                           org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("22000")))));
    }

    // ── Incident date validation ─────────────────────────────────────────────

    @Test
    void submitClaim_incidentDateBeforePolicyDate_returnsFormWithError() throws Exception {
        // Policy was submitted today; incident date set to yesterday — should be blocked
        UnderwritingRecord policy = issuedPolicy("CUST-2025-0120", "Pol-2025-120");
        String beforePolicy = policy.getSubmittedAt().toLocalDate().minusDays(1).toString();

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-120")
           .param("incidentDescription", "Backdated incident.")
           .param("claimAmount", "5000")
           .param("incidentDate", beforePolicy)
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    void submitClaim_incidentDateOnPolicyDate_isAllowed() throws Exception {
        UnderwritingRecord policy = issuedPolicy("CUST-2025-0121", "Pol-2025-121");
        String onPolicyDate = policy.getSubmittedAt().toLocalDate().toString();

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-121")
           .param("incidentDescription", "Incident on the day of purchase.")
           .param("claimAmount", "2000")
           .param("incidentDate", onPolicyDate)
           .param("atFault", "false"))
           .andExpect(view().name("claim-submitted"));
    }

    // ── /claim/policies AJAX endpoint ────────────────────────────────────────

    @Test
    void policiesByCustomerId_returnsMatchingPolicies() throws Exception {
        issuedPolicy("CUST-2025-0130", "Pol-2025-130");

        mvc.perform(get("/claim/policies").param("customerId", "CUST-2025-0130"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(content().string(containsString("Pol-2025-130")));
    }

    @Test
    void policiesByCustomerId_unknownId_returnsEmptyArray() throws Exception {
        mvc.perform(get("/claim/policies").param("customerId", "CUST-0000-0000"))
           .andExpect(status().isOk())
           .andExpect(content().string("[]"));
    }

    @Test
    void policiesByCustomerId_caseInsensitive_matchesUppercase() throws Exception {
        issuedPolicy("CUST-2025-0131", "Pol-2025-131");

        mvc.perform(get("/claim/policies").param("customerId", "cust-2025-0131"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Pol-2025-131")));
    }

    // ── Duplicate claim prevention ────────────────────────────────────────────

    @Test
    void submitClaim_whenPendingClaimExists_returnsFormWithError() throws Exception {
        issuedPolicy("CUST-2025-0110", "Pol-2025-110");

        // First claim — should succeed
        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-110")
           .param("incidentDescription", "First incident.")
           .param("claimAmount", "3000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(view().name("claim-submitted"));

        // Second claim — should be blocked while first is PENDING_REVIEW
        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-110")
           .param("incidentDescription", "Second incident.")
           .param("claimAmount", "1500")
           .param("incidentDate", "2025-07-01")
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    void submitClaim_afterApprovedClaim_isAllowed() throws Exception {
        issuedPolicy("CUST-2025-0111", "Pol-2025-111");

        // Save an already-resolved (APPROVED) claim — should not block new submission
        claimRepo.save(PolicyClaim.builder()
                .customerId("CUST-2025-0111")
                .policyNumber("Pol-2025-111")
                .companyName("TestFleet")
                .selectedTier("Basic")
                .incidentDescription("Old resolved incident.")
                .claimAmount(new BigDecimal("2000"))
                .incidentDate(LocalDate.now().minusDays(30))
                .atFault(false)
                .status("APPROVED")
                .submittedAt(LocalDateTime.now().minusDays(30))
                .build());

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2025-111")
           .param("incidentDescription", "New incident after approval.")
           .param("claimAmount", "4000")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(view().name("claim-submitted"));
    }

    // ── Coverage limit validation ─────────────────────────────────────────────

    @Test
    void submitClaim_coverageExhausted_returnsFormWithError() throws Exception {
        UnderwritingRecord policy = recordRepo.save(UnderwritingRecord.builder()
                .companyName("ExhaustedFleet")
                .phoneNumber("5550000000")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.20)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new java.math.BigDecimal("1.10"))
                .annualPremium(new java.math.BigDecimal("4500"))
                .workflowStatus("POLICY_ISSUED")
                .customerId("CUST-2026-7777")
                .policyNumber("Pol-2026-7777")
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusYears(1))
                .build());

        // Simulate coverage already exhausted: $10,000 approved on Basic policy
        claimRepo.save(PolicyClaim.builder()
                .customerId(policy.getCustomerId())
                .policyNumber(policy.getPolicyNumber())
                .companyName("ExhaustedFleet")
                .selectedTier("Basic")
                .incidentDescription("Major collision.")
                .claimAmount(new java.math.BigDecimal("10000"))
                .incidentDate(LocalDate.now().minusDays(5))
                .atFault(false)
                .status("APPROVED")
                .approvedAmount(new java.math.BigDecimal("10000"))
                .submittedAt(LocalDateTime.now().minusDays(5))
                .build());

        mvc.perform(post("/claim/submit").with(csrf())
           .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
           .param("reference", "Pol-2026-7777")
           .param("incidentDescription", "New incident.")
           .param("claimAmount", "500")
           .param("incidentDate", LocalDate.now().toString())
           .param("atFault", "false"))
           .andExpect(status().isOk())
           .andExpect(view().name("claim-form"))
           .andExpect(model().attributeExists("error"));
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private UnderwritingRecord issuedPolicy(String customerId, String policyNumber) {
        return recordRepo.save(UnderwritingRecord.builder()
                .companyName("TestFleet")
                .phoneNumber("5550000000")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.20)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new BigDecimal("1.10"))
                .annualPremium(new BigDecimal("4500"))
                .workflowStatus("POLICY_ISSUED")
                .customerId(customerId)
                .policyNumber(policyNumber)
                .build());
    }
}
