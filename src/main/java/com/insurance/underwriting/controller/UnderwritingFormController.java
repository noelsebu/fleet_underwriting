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
        String workflowStatus = riskCat.equals("LOW")
                ? "PENDING_CUSTOMER_ACCEPTANCE"
                : "PENDING_ADMIN_REVIEW";

        UnderwritingRecord record = UnderwritingRecord.builder()
                .companyName(request.getBusinessInfo().getCompanyName())
                .phoneNumber(request.getBusinessInfo().getPhoneNumber())
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

    @PostMapping("/underwriting/accept/{id}")
    public String acceptPolicy(@PathVariable Long id) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));

        if (!"PENDING_CUSTOMER_ACCEPTANCE".equals(record.getWorkflowStatus())) {
            return "redirect:/underwriting/result/" + id;
        }

        String customerId = String.format("CUST-%d-%04d", LocalDate.now().getYear(), record.getId());
        String policyNumber = String.format("Pol-%d-%d", LocalDate.now().getYear(), record.getId());

        record.setWorkflowStatus("POLICY_ISSUED");
        record.setCustomerId(customerId);
        record.setPolicyNumber(policyNumber);
        recordRepository.save(record);

        return "redirect:/underwriting/result/" + id;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("records", recordRepository.findAllByOrderBySubmittedAtDesc());
        return "history";
    }
}
