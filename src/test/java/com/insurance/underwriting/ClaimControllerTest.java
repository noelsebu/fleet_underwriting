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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
           .param("incidentDate", "2025-06-15")
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
           .param("incidentDate", "2025-07-01")
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
           .param("incidentDate", "2025-08-10")
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
           .param("incidentDate", "2025-09-01")
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
           .param("incidentDate", "2025-06-01")
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
           .param("incidentDate", "2025-06-01")
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
           .param("incidentDate", "2025-10-05")
           .param("atFault", "false"))
           .andExpect(model().attribute("claim",
                   org.hamcrest.Matchers.hasProperty("claimAmount",
                           org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("22000")))));
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
