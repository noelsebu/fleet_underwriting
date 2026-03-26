package com.insurance.underwriting;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UnderwritingRecordRepository recordRepo;
    @Autowired PolicyClaimRepository claimRepo;

    // ── Security ─────────────────────────────────────────────────────────────

    @Test
    void adminQueue_unauthenticated_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/queue"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminDashboard_unauthenticated_redirectsToLogin() throws Exception {
        mvc.perform(get("/admin/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/admin/login**"));
    }

    @Test
    void adminLogin_page_isPubliclyAccessible() throws Exception {
        mvc.perform(get("/admin/login"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/login"));
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void dashboard_returnsStats() throws Exception {
        recordRepo.save(pendingRecord("FleetA", "PENDING_ADMIN_REVIEW"));
        recordRepo.save(pendingRecord("FleetB", "POLICY_ISSUED"));
        claimRepo.save(pendingClaim("CUST-2025-0001", "Pol-2025-1", "PENDING_REVIEW"));

        mvc.perform(get("/admin/dashboard"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/dashboard"))
           .andExpect(model().attributeExists(
                   "totalSubmissions", "pendingReview", "policiesIssued",
                   "rejected", "totalRevenue", "totalClaimsPaid", "negotiationQueue",
                   "totalClaims", "claimsPending", "claimsApproved", "claimsRejected",
                   "recentSubmissions", "recentClaims"));
    }

    // ── Queue ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void queue_showsOnlyPendingAdminReview() throws Exception {
        recordRepo.save(pendingRecord("PendingCo", "PENDING_ADMIN_REVIEW"));
        recordRepo.save(pendingRecord("IssuedCo", "POLICY_ISSUED"));

        mvc.perform(get("/admin/queue"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/queue"))
           .andExpect(model().attributeExists("records"));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void queue_emptyWhenNoReviewItems() throws Exception {
        recordRepo.save(pendingRecord("IssuedOnly", "POLICY_ISSUED"));

        mvc.perform(get("/admin/queue"))
           .andExpect(status().isOk())
           .andExpect(model().attribute("records", hasSize(0)));
    }

    // ── All Submissions ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void allSubmissions_showsAllRecords() throws Exception {
        recordRepo.save(pendingRecord("Alpha", "PENDING_ADMIN_REVIEW"));
        recordRepo.save(pendingRecord("Beta", "POLICY_ISSUED"));
        recordRepo.save(pendingRecord("Gamma", "REJECTED"));

        mvc.perform(get("/admin/all"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/all"))
           .andExpect(model().attributeExists("records"));
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approve_setsStatusToPolicyIssued() throws Exception {
        UnderwritingRecord record = recordRepo.save(pendingRecord("ApproveCo", "PENDING_ADMIN_REVIEW"));

        mvc.perform(post("/admin/approve/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/queue"));

        UnderwritingRecord updated = recordRepo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("POLICY_ISSUED");
        assertThat(updated.getCustomerId()).startsWith("CUST-");
        assertThat(updated.getPolicyNumber()).startsWith("Pol-");
        assertThat(updated.getIssuedAt()).isNotNull();
        assertThat(updated.getExpiresAt()).isNotNull();
        assertThat(updated.getExpiresAt()).isAfter(updated.getIssuedAt());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approve_assignsCustomerIdAndPolicyNumber() throws Exception {
        UnderwritingRecord record = recordRepo.save(pendingRecord("PolicyFleet", "PENDING_ADMIN_REVIEW"));

        mvc.perform(post("/admin/approve/" + record.getId()).with(csrf()));

        UnderwritingRecord updated = recordRepo.findById(record.getId()).orElseThrow();
        assertThat(updated.getCustomerId()).matches("CUST-\\d{4}-\\d{4}");
        assertThat(updated.getPolicyNumber()).matches("Pol-\\d{4}-\\d+");
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void reject_setsStatusToRejected() throws Exception {
        UnderwritingRecord record = recordRepo.save(pendingRecord("RejectCo", "PENDING_ADMIN_REVIEW"));

        mvc.perform(post("/admin/reject/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/queue"));

        assertThat(recordRepo.findById(record.getId()).orElseThrow().getWorkflowStatus())
                .isEqualTo("REJECTED");
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void reject_withAdminComment_savesComment() throws Exception {
        UnderwritingRecord record = recordRepo.save(pendingRecord("CommentRejectCo", "PENDING_ADMIN_REVIEW"));

        mvc.perform(post("/admin/reject/" + record.getId()).with(csrf())
           .param("adminComment", "Too many at-fault claims."))
           .andExpect(status().is3xxRedirection());

        UnderwritingRecord updated = recordRepo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("REJECTED");
        assertThat(updated.getAdminComment()).isEqualTo("Too many at-fault claims.");
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approve_nonExistentRecord_returns4xx() throws Exception {
        mvc.perform(post("/admin/approve/99999").with(csrf()))
           .andExpect(status().is4xxClientError());
    }

    // ── Claims queue ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void claimsQueue_showsPendingClaims() throws Exception {
        claimRepo.save(pendingClaim("CUST-2025-0001", "Pol-2025-1", "PENDING_REVIEW"));
        claimRepo.save(pendingClaim("CUST-2025-0002", "Pol-2025-2", "APPROVED"));

        mvc.perform(get("/admin/claims"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/claims"))
           .andExpect(model().attributeExists("claims"));
    }

    // ── Approve claim ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approveClaim_setsStatusAndApprovedAmount() throws Exception {
        PolicyClaim claim = claimRepo.save(pendingClaim("CUST-2025-0001", "Pol-2025-1", "PENDING_REVIEW"));

        mvc.perform(post("/admin/claims/approve/" + claim.getId())
           .with(csrf())
           .param("approvedAmount", "2000.00")
           .param("adminNote", "Claim verified, approved."))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/claims"));

        PolicyClaim updated = claimRepo.findById(claim.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(updated.getApprovedAmount()).isEqualByComparingTo("2000.00");
        assertThat(updated.getAdminNote()).isEqualTo("Claim verified, approved.");
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approveClaim_withoutNote_stillApproves() throws Exception {
        PolicyClaim claim = claimRepo.save(pendingClaim("CUST-2025-0003", "Pol-2025-3", "PENDING_REVIEW"));

        mvc.perform(post("/admin/claims/approve/" + claim.getId())
           .with(csrf())
           .param("approvedAmount", "2500.00"))
           .andExpect(status().is3xxRedirection());

        assertThat(claimRepo.findById(claim.getId()).orElseThrow().getStatus()).isEqualTo("APPROVED");
    }

    // ── Reject claim ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void rejectClaim_setsStatusToRejected() throws Exception {
        PolicyClaim claim = claimRepo.save(pendingClaim("CUST-2025-0004", "Pol-2025-4", "PENDING_REVIEW"));

        mvc.perform(post("/admin/claims/reject/" + claim.getId())
           .with(csrf())
           .param("adminNote", "Insufficient documentation."))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/claims"));

        PolicyClaim updated = claimRepo.findById(claim.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("REJECTED");
        assertThat(updated.getAdminNote()).isEqualTo("Insufficient documentation.");
    }

    // ── Set Premium (negotiation) ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void setPremium_setsNegotiatedPremiumAndStatus() throws Exception {
        UnderwritingRecord record = recordRepo.save(pendingRecord("NegoCo", "NEGOTIATION_REQUESTED"));

        mvc.perform(post("/admin/set-premium/" + record.getId()).with(csrf())
           .param("negotiatedPremium", "3500.00"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/queue"));

        UnderwritingRecord updated = recordRepo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("NEGOTIATION_OFFERED");
        assertThat(updated.getNegotiatedPremium()).isEqualByComparingTo("3500.00");
    }

    // ── Coverage limit expiry ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approveClaim_exceedsCoverageLimit_expiresPolicy() throws Exception {
        // Basic tier limit = $10,000
        UnderwritingRecord policy = recordRepo.save(UnderwritingRecord.builder()
                .companyName("CovTestCo")
                .phoneNumber("5550000000")
                .email("cov@test.com")
                .selectedTier("Basic")
                .submittedAt(java.time.LocalDateTime.now())
                .riskScore(0.20)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new BigDecimal("1.10"))
                .annualPremium(new BigDecimal("4500"))
                .workflowStatus("POLICY_ISSUED")
                .policyNumber("Pol-2026-9999")
                .customerId("CUST-2026-9999")
                .issuedAt(java.time.LocalDateTime.now())
                .expiresAt(java.time.LocalDateTime.now().plusYears(1))
                .build());

        // Approve a claim that pushes total over $10k
        PolicyClaim claim = claimRepo.save(PolicyClaim.builder()
                .customerId(policy.getCustomerId())
                .policyNumber(policy.getPolicyNumber())
                .companyName("CovTestCo")
                .selectedTier("Basic")
                .incidentDescription("Major collision.")
                .claimAmount(new BigDecimal("11000"))
                .incidentDate(java.time.LocalDate.now().minusDays(5))
                .atFault(false)
                .status("PENDING_REVIEW")
                .submittedAt(java.time.LocalDateTime.now())
                .build());

        mvc.perform(post("/admin/claims/approve/" + claim.getId()).with(csrf())
           .param("approvedAmount", "11000.00"))
           .andExpect(status().is3xxRedirection());

        UnderwritingRecord updated = recordRepo.findById(policy.getId()).orElseThrow();
        assertThat(updated.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusSeconds(5));
        assertThat(updated.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusDays(1));
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approveClaim_belowCoverageLimit_doesNotExpirePolicy() throws Exception {
        UnderwritingRecord policy = recordRepo.save(UnderwritingRecord.builder()
                .companyName("SafeCovCo")
                .phoneNumber("5550000001")
                .email("safe@test.com")
                .selectedTier("Premium")
                .submittedAt(java.time.LocalDateTime.now())
                .riskScore(0.30)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new BigDecimal("1.10"))
                .annualPremium(new BigDecimal("5500"))
                .workflowStatus("POLICY_ISSUED")
                .policyNumber("Pol-2026-8888")
                .customerId("CUST-2026-8888")
                .issuedAt(java.time.LocalDateTime.now())
                .expiresAt(java.time.LocalDateTime.now().plusYears(1))
                .build());

        PolicyClaim claim = claimRepo.save(PolicyClaim.builder()
                .customerId(policy.getCustomerId())
                .policyNumber(policy.getPolicyNumber())
                .companyName("SafeCovCo")
                .selectedTier("Premium")
                .incidentDescription("Minor scratch.")
                .claimAmount(new BigDecimal("2000"))
                .incidentDate(java.time.LocalDate.now().minusDays(3))
                .atFault(false)
                .status("PENDING_REVIEW")
                .submittedAt(java.time.LocalDateTime.now())
                .build());

        mvc.perform(post("/admin/claims/approve/" + claim.getId()).with(csrf())
           .param("approvedAmount", "2000.00"))
           .andExpect(status().is3xxRedirection());

        UnderwritingRecord updated = recordRepo.findById(policy.getId()).orElseThrow();
        assertThat(updated.getExpiresAt()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void rejectClaim_nonExistent_returns4xx() throws Exception {
        mvc.perform(post("/admin/claims/reject/99999").with(csrf()))
           .andExpect(status().is4xxClientError());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UnderwritingRecord pendingRecord(String company, String status) {
        return UnderwritingRecord.builder()
                .companyName(company)
                .phoneNumber("5550000000")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.55)
                .riskCategory("MEDIUM")
                .recommendedAction("REVIEW")
                .premiumMultiplier(new BigDecimal("1.30"))
                .annualPremium(new BigDecimal("6000"))
                .workflowStatus(status)
                .build();
    }

    private PolicyClaim pendingClaim(String customerId, String policyNumber, String status) {
        return PolicyClaim.builder()
                .customerId(customerId)
                .policyNumber(policyNumber)
                .companyName("TestFleet")
                .selectedTier("Basic")
                .incidentDescription("Minor collision at parking lot.")
                .claimAmount(new BigDecimal("2500"))
                .incidentDate(LocalDate.now().minusDays(10))
                .atFault(false)
                .status(status)
                .submittedAt(LocalDateTime.now())
                .build();
    }
}
