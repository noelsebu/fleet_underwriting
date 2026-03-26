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

import java.util.Optional;

@Controller
@RequestMapping("/policy")
@RequiredArgsConstructor
public class PolicyLookupController {

    private final UnderwritingRecordRepository recordRepository;
    private final PolicyClaimRepository claimRepository;

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
        model.addAttribute("record", rec);
        model.addAttribute("claims",
                claimRepository.findByCustomerIdOrPolicyNumber(rec.getCustomerId(), rec.getPolicyNumber()));
        return "policy-detail";
    }
}
