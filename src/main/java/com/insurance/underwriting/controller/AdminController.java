package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UnderwritingRecordRepository recordRepository;
    private final PolicyClaimRepository claimRepository;

    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalSubmissions  = recordRepository.count();
        long pendingReview     = recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("PENDING_ADMIN_REVIEW").size();
        long policiesIssued    = recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("POLICY_ISSUED").size();
        long rejected          = recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("REJECTED").size();
        long pendingAcceptance = recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("PENDING_CUSTOMER_ACCEPTANCE").size();

        long totalClaims       = claimRepository.count();
        long claimsPending     = claimRepository.countByStatus("PENDING_REVIEW");
        long claimsApproved    = claimRepository.countByStatus("APPROVED");
        long claimsRejected    = claimRepository.countByStatus("REJECTED");

        List<UnderwritingRecord> recentSubmissions = recordRepository.findAllByOrderBySubmittedAtDesc()
                .stream().limit(5).toList();
        List<PolicyClaim> recentClaims = claimRepository.findAllByOrderBySubmittedAtDesc()
                .stream().limit(5).toList();

        var totalRevenue = recordRepository.sumAnnualPremiumForIssuedPolicies();

        model.addAttribute("totalSubmissions",  totalSubmissions);
        model.addAttribute("pendingReview",     pendingReview);
        model.addAttribute("policiesIssued",    policiesIssued);
        model.addAttribute("rejected",          rejected);
        model.addAttribute("pendingAcceptance", pendingAcceptance);
        model.addAttribute("totalRevenue",      totalRevenue);
        model.addAttribute("totalClaims",       totalClaims);
        model.addAttribute("claimsPending",     claimsPending);
        model.addAttribute("claimsApproved",    claimsApproved);
        model.addAttribute("claimsRejected",    claimsRejected);
        model.addAttribute("recentSubmissions", recentSubmissions);
        model.addAttribute("recentClaims",      recentClaims);

        return "admin/dashboard";
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("records",
                recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("PENDING_ADMIN_REVIEW"));
        return "admin/queue";
    }

    @GetMapping("/all")
    public String all(Model model) {
        model.addAttribute("records", recordRepository.findAllByOrderBySubmittedAtDesc());
        return "admin/all";
    }

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Long id) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));

        LocalDate today = LocalDate.now();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        record.setWorkflowStatus("POLICY_ISSUED");
        record.setCustomerId(String.format("CUST-%d-%04d", today.getYear(), record.getId()));
        record.setPolicyNumber(String.format("Pol-%d-%d", today.getYear(), record.getId()));
        record.setIssuedAt(now);
        record.setExpiresAt(now.plusYears(1));
        recordRepository.save(record);

        return "redirect:/admin/queue";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String adminComment) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        record.setWorkflowStatus("REJECTED");
        record.setAdminComment(adminComment);
        recordRepository.save(record);
        return "redirect:/admin/queue";
    }

    // ── Claims ────────────────────────────────────────────────────────────────

    @GetMapping("/claims")
    public String claims(Model model) {
        model.addAttribute("claims",
                claimRepository.findByStatusOrderBySubmittedAtDesc("PENDING_REVIEW"));
        return "admin/claims";
    }

    @PostMapping("/claims/approve/{id}")
    public String approveClaim(@PathVariable Long id,
                               @RequestParam(required = false) String adminNote) {
        PolicyClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
        claim.setStatus("APPROVED");
        claim.setAdminNote(adminNote);
        claimRepository.save(claim);
        return "redirect:/admin/claims";
    }

    @PostMapping("/claims/reject/{id}")
    public String rejectClaim(@PathVariable Long id,
                              @RequestParam(required = false) String adminNote) {
        PolicyClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
        claim.setStatus("REJECTED");
        claim.setAdminNote(adminNote);
        claimRepository.save(claim);
        return "redirect:/admin/claims";
    }
}
