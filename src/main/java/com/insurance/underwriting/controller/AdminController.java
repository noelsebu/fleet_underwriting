package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

        var totalRevenue      = recordRepository.sumAnnualPremiumForIssuedPolicies();
        var totalClaimsPaid   = claimRepository.sumAllApprovedClaimAmounts();
        long negotiationQueue = recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("NEGOTIATION_REQUESTED").size();

        model.addAttribute("totalSubmissions",  totalSubmissions);
        model.addAttribute("pendingReview",     pendingReview);
        model.addAttribute("policiesIssued",    policiesIssued);
        model.addAttribute("rejected",          rejected);
        model.addAttribute("pendingAcceptance", pendingAcceptance);
        model.addAttribute("totalRevenue",      totalRevenue);
        model.addAttribute("totalClaimsPaid",   totalClaimsPaid);
        model.addAttribute("negotiationQueue",  negotiationQueue);
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
        model.addAttribute("records", recordRepository.findPendingReviewQueue());
        return "admin/queue";
    }

    @GetMapping("/negotiations")
    public String negotiations(Model model) {
        model.addAttribute("negotiations",
                recordRepository.findByWorkflowStatusOrderBySubmittedAtDesc("NEGOTIATION_REQUESTED"));
        return "admin/negotiations";
    }

    /** Admin accepts negotiation and directly issues the policy without waiting for customer. */
    @PostMapping("/accept-negotiation/{id}")
    public String acceptNegotiation(@PathVariable Long id,
                                    @RequestParam BigDecimal negotiatedPremium) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        record.setNegotiatedPremium(negotiatedPremium);
        record.setAnnualPremium(negotiatedPremium);
        record.setWorkflowStatus("POLICY_ISSUED");
        record.setCustomerId(String.format("CUST-%d-%04d", java.time.LocalDate.now().getYear(), record.getId()));
        record.setPolicyNumber(String.format("Pol-%d-%d", java.time.LocalDate.now().getYear(), record.getId()));
        record.setIssuedAt(now);
        record.setExpiresAt(now.plusYears(1));
        recordRepository.save(record);
        return "redirect:/admin/negotiations";
    }

    /** Admin sets a negotiated premium for a customer's request. */
    @PostMapping("/set-premium/{id}")
    public String setPremium(@PathVariable Long id,
                             @RequestParam BigDecimal negotiatedPremium) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        record.setNegotiatedPremium(negotiatedPremium);
        record.setWorkflowStatus("NEGOTIATION_OFFERED");
        recordRepository.save(record);
        return "redirect:/admin/negotiations";
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
                               @RequestParam BigDecimal approvedAmount,
                               @RequestParam(required = false) String adminNote) {
        PolicyClaim claim = claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
        claim.setStatus("APPROVED");
        claim.setApprovedAmount(approvedAmount);
        claim.setAdminNote(adminNote);
        claimRepository.save(claim);

        // Check if cumulative approved claims exceed the coverage limit for this policy
        recordRepository.findByPolicyNumber(claim.getPolicyNumber()).ifPresent(policy -> {
            BigDecimal totalApproved = claimRepository.sumApprovedAmountByPolicyNumber(claim.getPolicyNumber())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal limit = coverageLimit(policy.getSelectedTier());
            if (totalApproved.compareTo(limit) >= 0) {
                policy.setExpiresAt(java.time.LocalDateTime.now());
                recordRepository.save(policy);
            }
        });

        return "redirect:/admin/claims";
    }

    private static BigDecimal coverageLimit(String tier) {
        if (tier == null) return new BigDecimal("10000");
        switch (tier.toLowerCase()) {
            case "premium": return new BigDecimal("15000");
            case "diamond": return new BigDecimal("20000");
            default:        return new BigDecimal("10000");
        }
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
