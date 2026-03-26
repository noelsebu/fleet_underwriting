package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.model.GeneratedPolicy;
import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import com.insurance.underwriting.service.PolicyGenerationService;
import com.insurance.underwriting.service.QuotationService;
import com.insurance.underwriting.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class UnderwritingFormController {

    private final RiskScoringService scoringService;
    private final QuotationService quotationService;
    private final PolicyGenerationService policyGenerationService;
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

        Optional<GeneratedPolicy> policy = policyGenerationService.generate(chosenQuote, response.getRiskCategory().name());

        UnderwritingRecord record = UnderwritingRecord.builder()
                .companyName(request.getBusinessInfo().getCompanyName())
                .selectedTier(selectedTier)
                .submittedAt(LocalDateTime.now())
                .riskScore(response.getRiskScore())
                .riskCategory(response.getRiskCategory().name())
                .recommendedAction(response.getRecommendedAction().name())
                .premiumMultiplier(response.getPremiumMultiplier())
                .policyNumber(policy.map(GeneratedPolicy::getPolicyNumber).orElse(null))
                .policyStatus(policy.map(GeneratedPolicy::getStatus).orElse("REJECTED"))
                .annualPremium(policy.map(GeneratedPolicy::getAnnualPremium).orElse(null))
                .build();
        recordRepository.save(record);

        model.addAttribute("response", response);
        model.addAttribute("chosenQuote", chosenQuote);
        model.addAttribute("policy", policy.orElse(null));
        return "underwriting-result";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("records", recordRepository.findAllByOrderBySubmittedAtDesc());
        return "history";
    }
}
