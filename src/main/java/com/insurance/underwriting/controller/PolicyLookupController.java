package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.PolicyClaimRepository;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/policy")
@RequiredArgsConstructor
public class PolicyLookupController {

    private final UnderwritingRecordRepository recordRepository;
    private final PolicyClaimRepository claimRepository;

    private static BigDecimal coverageLimit(String tier) {
        if (tier == null) return new BigDecimal("10000");
        switch (tier.toLowerCase()) {
            case "premium": return new BigDecimal("15000");
            case "diamond": return new BigDecimal("20000");
            default:        return new BigDecimal("10000");
        }
    }

    @GetMapping("/lookup")
    public String lookupForm() {
        return "policy-lookup";
    }

    @GetMapping("/check")
    public String checkPolicy(@RequestParam String reference, Model model) {
        Optional<UnderwritingRecord> result = recordRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .filter(r -> reference.equalsIgnoreCase(r.getCustomerId())
                          || reference.equalsIgnoreCase(r.getPolicyNumber()))
                .findFirst();

        if (result.isEmpty()) {
            model.addAttribute("error", "No policy found for reference: " + reference);
            return "policy-lookup";
        }

        UnderwritingRecord rec = result.get();
        BigDecimal coverageLimit = coverageLimit(rec.getSelectedTier());
        BigDecimal approvedTotal = rec.getPolicyNumber() != null
                ? claimRepository.sumApprovedAmountByPolicyNumber(rec.getPolicyNumber())
                : BigDecimal.ZERO;
        int usagePct = approvedTotal.compareTo(coverageLimit) >= 0 ? 100
                : approvedTotal.multiply(BigDecimal.valueOf(100))
                               .divide(coverageLimit, 0, java.math.RoundingMode.HALF_UP)
                               .intValue();
        model.addAttribute("record", rec);
        model.addAttribute("claims",
                claimRepository.findByCustomerIdOrPolicyNumber(rec.getCustomerId(), rec.getPolicyNumber()));
        model.addAttribute("coverageLimit", coverageLimit);
        model.addAttribute("approvedClaimsTotal", approvedTotal);
        model.addAttribute("coverageUsagePct", usagePct);
        return "policy-detail";
    }
}
