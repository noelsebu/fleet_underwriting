package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import com.insurance.underwriting.service.QuotationService;
import com.insurance.underwriting.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class UnderwritingFormController {

    private final RiskScoringService scoringService;
    private final QuotationService quotationService;
    private final UnderwritingRecordRepository recordRepository;

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/underwriting")
    public String showForm(@RequestParam(defaultValue = "Basic") String tier, Model model) {
        RiskScoreRequest request = new RiskScoreRequest();
        request.setBusinessInfo(new RiskScoreRequest.BusinessInfo());
        request.setFleetDetails(new RiskScoreRequest.FleetDetails());
        request.setDriverPool(new RiskScoreRequest.DriverPool());
        request.setClaimsHistory(new RiskScoreRequest.ClaimsHistory());
        model.addAttribute("request", request);
        model.addAttribute("selectedTier", tier);
        return "underwriting-form";
    }

    @PostMapping("/underwriting")
    public String submitForm(@Valid @ModelAttribute("request") RiskScoreRequest request,
                             BindingResult bindingResult,
                             @RequestParam(defaultValue = "Basic") String selectedTier,
                             Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("selectedTier", selectedTier);
            return "underwriting-form";
        }

        RiskScoreResponse response = scoringService.score(request);
        List<PolicyQuote> quotes = quotationService.generateQuotes(response);

        PolicyQuote chosenQuote = quotes.stream()
                .filter(q -> q.getTier().equalsIgnoreCase(selectedTier))
                .findFirst()
                .orElse(quotes.get(0));

        String riskCat = response.getRiskCategory().name();
        String workflowStatus;
        if ("LOW".equals(riskCat)) {
            workflowStatus = "PENDING_CUSTOMER_ACCEPTANCE";
        } else if ("MEDIUM".equals(riskCat)) {
            workflowStatus = "PENDING_ADMIN_REVIEW";
        } else {
            // HIGH risk: user must explicitly request a policy review
            workflowStatus = "HIGH_RISK_REVIEW";
        }

        UnderwritingRecord record = UnderwritingRecord.builder()
                .companyName(request.getBusinessInfo().getCompanyName())
                .phoneNumber(request.getBusinessInfo().getPhoneNumber())
                .email(request.getBusinessInfo().getEmail())
                .selectedTier(selectedTier)
                .submittedAt(LocalDateTime.now())
                .riskScore(response.getRiskScore())
                .riskCategory(riskCat)
                .recommendedAction(response.getRecommendedAction().name())
                .premiumMultiplier(response.getPremiumMultiplier())
                .annualPremium(chosenQuote.getQuotedPremium())
                .workflowStatus(workflowStatus)
                .decisionFactors(response.getDecisionFactors())
                .coverages(chosenQuote.getCoverages())
                .build();

        recordRepository.save(record);
        return "redirect:/underwriting/result/" + record.getId();
    }

    @GetMapping("/underwriting/result/{id}")
    public String showResult(@PathVariable Long id, Model model) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        model.addAttribute("record", record);
        return "underwriting-result";
    }

    /** LOW risk: customer accepts and policy is immediately issued. */
    @PostMapping("/underwriting/accept/{id}")
    public String acceptPolicy(@PathVariable Long id) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));

        if (!"PENDING_CUSTOMER_ACCEPTANCE".equals(record.getWorkflowStatus())) {
            return "redirect:/underwriting/result/" + id;
        }

        LocalDateTime now = LocalDateTime.now();
        record.setWorkflowStatus("POLICY_ISSUED");
        record.setCustomerId(String.format("CUST-%d-%04d", LocalDate.now().getYear(), record.getId()));
        record.setPolicyNumber(String.format("Pol-%d-%d", LocalDate.now().getYear(), record.getId()));
        record.setIssuedAt(now);
        record.setExpiresAt(now.plusYears(1));
        recordRepository.save(record);

        return "redirect:/underwriting/result/" + id;
    }

    /** HIGH risk: user explicitly requests a policy review — assigns a tracking number. */
    @PostMapping("/underwriting/request/{id}")
    public String requestPolicyReview(@PathVariable Long id) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));

        if (!"HIGH_RISK_REVIEW".equals(record.getWorkflowStatus())) {
            return "redirect:/underwriting/result/" + id;
        }

        record.setTrackingNumber(String.format("REQ-%d-%d", LocalDate.now().getYear(), record.getId()));
        record.setWorkflowStatus("PENDING_ADMIN_REVIEW");
        recordRepository.save(record);

        return "redirect:/underwriting/result/" + id;
    }

    /** Tracking page: look up a HIGH-risk request by tracking number. */
    @GetMapping("/underwriting/track")
    public String trackRequest(@RequestParam(required = false) String trackingNumber, Model model) {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            recordRepository.findByTrackingNumber(trackingNumber.trim().toUpperCase())
                    .ifPresentOrElse(
                            r -> model.addAttribute("record", r),
                            () -> model.addAttribute("error", "No request found for tracking number: " + trackingNumber)
                    );
        }
        return "underwriting-track";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("records", recordRepository.findAllByOrderBySubmittedAtDesc());
        return "history";
    }
}
