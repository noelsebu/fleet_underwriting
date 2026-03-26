package com.insurance.underwriting.controller;

import com.insurance.underwriting.model.GeneratedPolicy;
import com.insurance.underwriting.model.PolicyQuote;
import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.service.PolicyGenerationService;
import com.insurance.underwriting.service.QuotationService;
import com.insurance.underwriting.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class UnderwritingFormController {

    private final RiskScoringService scoringService;
    private final QuotationService quotationService;
    private final PolicyGenerationService policyGenerationService;

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

        model.addAttribute("response", response);
        model.addAttribute("chosenQuote", chosenQuote);
        model.addAttribute("policy", policy.orElse(null));
        return "underwriting-result";
    }
}
