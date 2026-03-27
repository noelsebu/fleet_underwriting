package com.insurance.underwriting;

import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnderwritingFormControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UnderwritingRecordRepository repo;

    // ── GET / (landing) ──────────────────────────────────────────────────────

    @Test
    void landingPage_returns200() throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk())
           .andExpect(view().name("landing"));
    }

    // ── GET /underwriting ────────────────────────────────────────────────────

    @Test
    void underwritingForm_defaultTier_isBasic() throws Exception {
        mvc.perform(get("/underwriting"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-form"))
           .andExpect(model().attributeExists("request"))
           .andExpect(model().attribute("selectedTier", "Basic"));
    }

    @Test
    void underwritingForm_customTier_isPremium() throws Exception {
        mvc.perform(get("/underwriting").param("tier", "Premium"))
           .andExpect(status().isOk())
           .andExpect(model().attribute("selectedTier", "Premium"));
    }

    // ── POST /underwriting ───────────────────────────────────────────────────

    @Test
    void submitForm_lowRisk_redirectsToResult() throws Exception {
        mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "SafeFleet Ltd")
           .param("businessInfo.yearsInOperation", "15")
           .param("businessInfo.creditScore", "780")
           .param("businessInfo.warZone", "false")
           .param("businessInfo.phoneNumber", "1234567890")
           .param("businessInfo.email", "safe@fleet.com")
           .param("fleetDetails.totalVehicles", "10")
           .param("fleetDetails.averageVehicleAgeYears", "2.0")
           .param("fleetDetails.primaryVehicleType", "SEDAN")
           .param("driverPool.totalDrivers", "8")
           .param("driverPool.averageDriverAge", "40.0")
           .param("claimsHistory.claimsLast3Years", "0")
           .param("claimsHistory.totalClaimAmount", "0")
           .param("claimsHistory.atFaultCount", "0")
           .param("selectedTier", "Basic"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("/underwriting/result/*"));
    }

    @Test
    void submitForm_mediumRisk_setsHighRiskReviewStatus() throws Exception {
        var result = mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "MediumFleet Ltd")
           .param("businessInfo.yearsInOperation", "3")
           .param("businessInfo.creditScore", "620")
           .param("businessInfo.warZone", "false")
           .param("businessInfo.phoneNumber", "5550001111")
           .param("businessInfo.email", "medium@fleet.com")
           .param("fleetDetails.totalVehicles", "15")
           .param("fleetDetails.averageVehicleAgeYears", "6.0")
           .param("fleetDetails.primaryVehicleType", "TRUCK")
           .param("driverPool.totalDrivers", "12")
           .param("driverPool.averageDriverAge", "28.0")
           .param("claimsHistory.claimsLast3Years", "4")
           .param("claimsHistory.totalClaimAmount", "40000")
           .param("claimsHistory.atFaultCount", "2")
           .param("selectedTier", "Premium"))
           .andExpect(status().is3xxRedirection())
           .andReturn();

        Long id = Long.parseLong(result.getResponse().getRedirectedUrl().replace("/underwriting/result/", ""));
        UnderwritingRecord saved = repo.findById(id).orElseThrow();
        // Both MEDIUM and HIGH skip straight to HIGH_RISK_REVIEW (manual review required)
        assertThat(saved.getWorkflowStatus()).isEqualTo("HIGH_RISK_REVIEW");
        assertThat(saved.getRiskCategory()).isNotEqualTo("LOW");
        assertThat(saved.getTrackingNumber()).isNull();
    }

    @Test
    void submitForm_highRisk_setsHighRiskReviewStatus() throws Exception {
        var result = mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "RiskyFleet LLC")
           .param("businessInfo.yearsInOperation", "1")
           .param("businessInfo.creditScore", "510")
           .param("businessInfo.warZone", "true")
           .param("businessInfo.phoneNumber", "9876543210")
           .param("businessInfo.email", "risky@fleet.com")
           .param("fleetDetails.totalVehicles", "30")
           .param("fleetDetails.averageVehicleAgeYears", "12.0")
           .param("fleetDetails.primaryVehicleType", "TRUCK")
           .param("driverPool.totalDrivers", "60")
           .param("driverPool.averageDriverAge", "22.0")
           .param("claimsHistory.claimsLast3Years", "15")
           .param("claimsHistory.totalClaimAmount", "300000")
           .param("claimsHistory.atFaultCount", "10")
           .param("selectedTier", "Premium"))
           .andExpect(status().is3xxRedirection())
           .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/underwriting/result/");

        Long id = Long.parseLong(location.replace("/underwriting/result/", ""));
        UnderwritingRecord saved = repo.findById(id).orElseThrow();
        assertThat(saved.getWorkflowStatus()).isEqualTo("HIGH_RISK_REVIEW");
        assertThat(saved.getRiskCategory()).isEqualTo("HIGH");
        assertThat(saved.getTrackingNumber()).isNull(); // no tracking number yet
    }

    @Test
    void submitForm_missingEmail_returnsFormWithErrors() throws Exception {
        mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "EmaillessFleet")
           .param("businessInfo.yearsInOperation", "10")
           .param("businessInfo.creditScore", "700")
           .param("businessInfo.phoneNumber", "1234567890")
           // email intentionally omitted
           .param("fleetDetails.totalVehicles", "10")
           .param("fleetDetails.averageVehicleAgeYears", "3.0")
           .param("fleetDetails.primaryVehicleType", "SEDAN")
           .param("driverPool.totalDrivers", "8")
           .param("driverPool.averageDriverAge", "35.0")
           .param("claimsHistory.claimsLast3Years", "0")
           .param("claimsHistory.totalClaimAmount", "0")
           .param("claimsHistory.atFaultCount", "0"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-form"))
           .andExpect(model().hasErrors());
    }

    @Test
    void submitForm_zeroClaimsWithNonZeroAmount_returnsFormWithErrors() throws Exception {
        mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "ZeroClaimsFleet")
           .param("businessInfo.yearsInOperation", "5")
           .param("businessInfo.creditScore", "700")
           .param("businessInfo.phoneNumber", "1234567890")
           .param("businessInfo.email", "zero@fleet.com")
           .param("fleetDetails.totalVehicles", "10")
           .param("fleetDetails.averageVehicleAgeYears", "3.0")
           .param("fleetDetails.primaryVehicleType", "SEDAN")
           .param("driverPool.totalDrivers", "8")
           .param("driverPool.averageDriverAge", "35.0")
           .param("claimsHistory.claimsLast3Years", "0")
           .param("claimsHistory.totalClaimAmount", "5000")  // invalid: 0 claims but non-zero amount
           .param("claimsHistory.atFaultCount", "0"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-form"))
           .andExpect(model().hasErrors());
    }

    @Test
    void submitForm_missingCompanyName_returnsFormWithErrors() throws Exception {
        mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           // companyName intentionally omitted
           .param("businessInfo.yearsInOperation", "10")
           .param("businessInfo.creditScore", "700")
           .param("businessInfo.phoneNumber", "1234567890")
           .param("businessInfo.email", "test@fleet.com")
           .param("fleetDetails.totalVehicles", "10")
           .param("fleetDetails.averageVehicleAgeYears", "3.0")
           .param("fleetDetails.primaryVehicleType", "SEDAN")
           .param("driverPool.totalDrivers", "8")
           .param("driverPool.averageDriverAge", "35.0")
           .param("claimsHistory.claimsLast3Years", "0")
           .param("claimsHistory.totalClaimAmount", "0")
           .param("claimsHistory.atFaultCount", "0"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-form"))
           .andExpect(model().hasErrors());
    }

    @Test
    void submitForm_lowRisk_setsWorkflowStatusToPendingCustomerAcceptance() throws Exception {
        var result = mvc.perform(post("/underwriting").with(csrf())
           .contentType(MediaType.APPLICATION_FORM_URLENCODED)
           .param("businessInfo.companyName", "GreenFleet Co")
           .param("businessInfo.yearsInOperation", "12")
           .param("businessInfo.creditScore", "760")
           .param("businessInfo.warZone", "false")
           .param("businessInfo.phoneNumber", "5551234567")
           .param("businessInfo.email", "green@fleet.com")
           .param("fleetDetails.totalVehicles", "8")
           .param("fleetDetails.averageVehicleAgeYears", "2.0")
           .param("fleetDetails.primaryVehicleType", "SEDAN")
           .param("driverPool.totalDrivers", "6")
           .param("driverPool.averageDriverAge", "38.0")
           .param("claimsHistory.claimsLast3Years", "0")
           .param("claimsHistory.totalClaimAmount", "0")
           .param("claimsHistory.atFaultCount", "0")
           .param("selectedTier", "Basic"))
           .andExpect(status().is3xxRedirection())
           .andReturn();

        Long id = Long.parseLong(result.getResponse().getRedirectedUrl().replace("/underwriting/result/", ""));
        UnderwritingRecord saved = repo.findById(id).orElseThrow();
        assertThat(saved.getWorkflowStatus()).isEqualTo("PENDING_CUSTOMER_ACCEPTANCE");
        assertThat(saved.getRiskCategory()).isEqualTo("LOW");
        assertThat(saved.getCompanyName()).isEqualTo("GreenFleet Co");
        assertThat(saved.getEmail()).isEqualTo("green@fleet.com");
    }

    // ── GET /underwriting/result/{id} ────────────────────────────────────────

    @Test
    void resultPage_existingRecord_rendersView() throws Exception {
        UnderwritingRecord record = savedRecord("ResultFleet", "PENDING_CUSTOMER_ACCEPTANCE");

        mvc.perform(get("/underwriting/result/" + record.getId()))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-result"))
           .andExpect(model().attributeExists("record"));
    }

    @Test
    void resultPage_nonExistentId_showsErrorPage() throws Exception {
        mvc.perform(get("/underwriting/result/99999"))
           .andExpect(status().is4xxClientError());
    }

    // ── POST /underwriting/accept/{id} ───────────────────────────────────────

    @Test
    void acceptPolicy_lowRisk_issuesPolicy() throws Exception {
        UnderwritingRecord record = savedRecord("AcceptFleet", "PENDING_CUSTOMER_ACCEPTANCE");

        mvc.perform(post("/underwriting/accept/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/underwriting/result/" + record.getId()));

        UnderwritingRecord updated = repo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("POLICY_ISSUED");
        assertThat(updated.getCustomerId()).startsWith("CUST-");
        assertThat(updated.getPolicyNumber()).startsWith("Pol-");
        assertThat(updated.getIssuedAt()).isNotNull();
        assertThat(updated.getExpiresAt()).isNotNull();
        assertThat(updated.getExpiresAt()).isAfter(updated.getIssuedAt());
    }

    @Test
    void acceptPolicy_alreadyIssued_doesNotChangeStatus() throws Exception {
        UnderwritingRecord record = savedRecord("AlreadyIssuedFleet", "POLICY_ISSUED");
        record.setCustomerId("CUST-2025-0001");
        record.setPolicyNumber("Pol-2025-1");
        repo.save(record);

        mvc.perform(post("/underwriting/accept/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection());

        assertThat(repo.findById(record.getId()).orElseThrow().getWorkflowStatus())
                .isEqualTo("POLICY_ISSUED");
    }

    @Test
    void acceptPolicy_pendingAdminReview_doesNotIssue() throws Exception {
        UnderwritingRecord record = savedRecord("MediumFleet", "PENDING_ADMIN_REVIEW");

        mvc.perform(post("/underwriting/accept/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection());

        // status should remain unchanged since it is not PENDING_CUSTOMER_ACCEPTANCE
        assertThat(repo.findById(record.getId()).orElseThrow().getWorkflowStatus())
                .isEqualTo("PENDING_ADMIN_REVIEW");
    }

    // ── POST /underwriting/negotiate/{id} ────────────────────────────────────

    @Test
    void negotiatePolicy_lowRisk_setsNegotiationRequestedStatusAndAssignsTrackingNumber() throws Exception {
        UnderwritingRecord record = savedRecord("NegotiateFleet", "PENDING_CUSTOMER_ACCEPTANCE");

        mvc.perform(post("/underwriting/negotiate/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/underwriting/result/" + record.getId()));

        UnderwritingRecord updated = repo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("NEGOTIATION_REQUESTED");
        assertThat(updated.getTrackingNumber()).matches("NEG-\\d{4}-\\d+");
    }

    @Test
    void negotiatePolicy_nonLowRisk_doesNotChangeStatus() throws Exception {
        UnderwritingRecord record = savedRecord("AlreadyReviewFleet", "PENDING_ADMIN_REVIEW");

        mvc.perform(post("/underwriting/negotiate/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection());

        assertThat(repo.findById(record.getId()).orElseThrow().getWorkflowStatus())
                .isEqualTo("PENDING_ADMIN_REVIEW");
    }

    // ── POST /underwriting/accept-offer/{id} ─────────────────────────────────

    @Test
    void acceptOffer_negotiationOffered_issuesPolicyWithNegotiatedPremium() throws Exception {
        UnderwritingRecord record = savedRecord("OfferFleet", "NEGOTIATION_OFFERED");
        record.setNegotiatedPremium(new BigDecimal("3200"));
        repo.save(record);

        mvc.perform(post("/underwriting/accept-offer/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/underwriting/result/" + record.getId()));

        UnderwritingRecord updated = repo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("POLICY_ISSUED");
        assertThat(updated.getAnnualPremium()).isEqualByComparingTo("3200");
        assertThat(updated.getIssuedAt()).isNotNull();
        assertThat(updated.getExpiresAt()).isNotNull();
    }

    // ── POST /underwriting/request/{id} ──────────────────────────────────────

    @Test
    void requestPolicyReview_highRisk_assignsTrackingNumberAndMovesToPendingReview() throws Exception {
        UnderwritingRecord record = savedRecord("HighRiskFleet", "HIGH_RISK_REVIEW");

        mvc.perform(post("/underwriting/request/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/underwriting/result/" + record.getId()));

        UnderwritingRecord updated = repo.findById(record.getId()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo("PENDING_ADMIN_REVIEW");
        assertThat(updated.getTrackingNumber()).matches("REQ-\\d{4}-\\d+");
    }

    @Test
    void requestPolicyReview_nonHighRisk_doesNotChangeStatus() throws Exception {
        UnderwritingRecord record = savedRecord("LowRiskFleet", "PENDING_CUSTOMER_ACCEPTANCE");

        mvc.perform(post("/underwriting/request/" + record.getId()).with(csrf()))
           .andExpect(status().is3xxRedirection());

        assertThat(repo.findById(record.getId()).orElseThrow().getWorkflowStatus())
                .isEqualTo("PENDING_CUSTOMER_ACCEPTANCE");
    }

    // ── GET /underwriting/track ───────────────────────────────────────────────

    @Test
    void trackPage_noParam_rendersEmptyTrackPage() throws Exception {
        mvc.perform(get("/underwriting/track"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-track"))
           .andExpect(model().attributeDoesNotExist("record"));
    }

    @Test
    void trackPage_validTrackingNumber_rendersRecord() throws Exception {
        UnderwritingRecord record = savedRecord("TrackableFleet", "PENDING_ADMIN_REVIEW");
        record.setTrackingNumber("REQ-2026-" + record.getId());
        repo.save(record);

        mvc.perform(get("/underwriting/track").param("trackingNumber", "REQ-2026-" + record.getId()))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-track"))
           .andExpect(model().attributeExists("record"));
    }

    @Test
    void trackPage_unknownTrackingNumber_rendersError() throws Exception {
        mvc.perform(get("/underwriting/track").param("trackingNumber", "REQ-9999-99999"))
           .andExpect(status().isOk())
           .andExpect(view().name("underwriting-track"))
           .andExpect(model().attributeExists("error"));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private UnderwritingRecord savedRecord(String company, String status) {
        return repo.save(UnderwritingRecord.builder()
                .companyName(company)
                .phoneNumber("5550000000")
                .email("test@fleet.com")
                .selectedTier("Basic")
                .submittedAt(LocalDateTime.now())
                .riskScore(0.20)
                .riskCategory("LOW")
                .recommendedAction("APPROVE")
                .premiumMultiplier(new BigDecimal("1.10"))
                .annualPremium(new BigDecimal("4500"))
                .workflowStatus(status)
                .build());
    }
}
