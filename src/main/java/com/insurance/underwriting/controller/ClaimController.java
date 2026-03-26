package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.PolicyClaim;
import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/claim")
@RequiredArgsConstructor
public class ClaimController {

    private final PolicyClaimRepository claimRepository;
    private final UnderwritingRecordRepository recordRepository;

    @GetMapping("/new")
    public String claimForm() {
        return "claim-form";
    }

    /**
     * AJAX endpoint: returns all POLICY_ISSUED records for a given customer ID.
     * Used by the claim form to populate the policy dropdown.
     */
    @GetMapping("/policies")
    @ResponseBody
    public List<Map<String, String>> policiesByCustomerId(@RequestParam String customerId) {
        return recordRepository
                .findByCustomerIdAndWorkflowStatus(customerId.trim().toUpperCase(), "POLICY_ISSUED")
                .stream()
                .map(r -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("policyNumber", r.getPolicyNumber());
                    m.put("companyName",  r.getCompanyName());
                    m.put("selectedTier", r.getSelectedTier());
                    m.put("issuedFrom",   r.getSubmittedAt().toLocalDate().toString());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/submit")
    public String submitClaim(
            @RequestParam String reference,
            @RequestParam String incidentDescription,
            @RequestParam BigDecimal claimAmount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDate,
            @RequestParam(defaultValue = "false") boolean atFault,
            Model model) {

        Optional<UnderwritingRecord> record = recordRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .filter(r -> "POLICY_ISSUED".equals(r.getWorkflowStatus())
                        && (reference.equalsIgnoreCase(r.getCustomerId())
                         || reference.equalsIgnoreCase(r.getPolicyNumber())))
                .findFirst();

        if (record.isEmpty()) {
            model.addAttribute("error", "No active policy found for: " + reference
                    + ". Please check your Customer ID or Policy Number.");
            return "claim-form";
        }

        UnderwritingRecord policy = record.get();

        // ── Date validation: incident must not predate the policy ─────────────
        LocalDate policyDate = policy.getSubmittedAt().toLocalDate();
        if (incidentDate.isBefore(policyDate)) {
            model.addAttribute("error",
                    "Incident date cannot be before the policy issue date ("
                    + policyDate + "). Please enter a valid date.");
            return "claim-form";
        }

        // ── Block if a pending claim already exists ───────────────────────────
        boolean hasPendingClaim = claimRepository
                .findByCustomerIdOrPolicyNumber(policy.getCustomerId(), policy.getPolicyNumber())
                .stream()
                .anyMatch(c -> "PENDING_REVIEW".equals(c.getStatus()));

        if (hasPendingClaim) {
            model.addAttribute("error", "A claim for policy " + policy.getPolicyNumber()
                    + " is already under review. You may not submit another claim until the current one has been resolved.");
            return "claim-form";
        }

        PolicyClaim claim = PolicyClaim.builder()
                .customerId(policy.getCustomerId())
                .policyNumber(policy.getPolicyNumber())
                .companyName(policy.getCompanyName())
                .selectedTier(policy.getSelectedTier())
                .incidentDescription(incidentDescription)
                .claimAmount(claimAmount)
                .incidentDate(incidentDate)
                .atFault(atFault)
                .status("PENDING_REVIEW")
                .submittedAt(LocalDateTime.now())
                .build();

        claimRepository.save(claim);
        model.addAttribute("claim", claim);
        return "claim-submitted";
    }
}
