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
                   "rejected", "totalClaims", "claimsPending",
                   "claimsApproved", "claimsRejected",
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
    void approveClaim_setsStatusToApproved() throws Exception {
        PolicyClaim claim = claimRepo.save(pendingClaim("CUST-2025-0001", "Pol-2025-1", "PENDING_REVIEW"));

        mvc.perform(post("/admin/claims/approve/" + claim.getId())
           .with(csrf())
           .param("adminNote", "Claim verified, approved."))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/admin/claims"));

        PolicyClaim updated = claimRepo.findById(claim.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("APPROVED");
        assertThat(updated.getAdminNote()).isEqualTo("Claim verified, approved.");
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void approveClaim_withoutNote_stillApproves() throws Exception {
        PolicyClaim claim = claimRepo.save(pendingClaim("CUST-2025-0003", "Pol-2025-3", "PENDING_REVIEW"));

        mvc.perform(post("/admin/claims/approve/" + claim.getId()).with(csrf()))
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
