package com.insurance.underwriting.controller;

import com.insurance.underwriting.model.RiskScoreRequest;
import com.insurance.underwriting.model.RiskScoreResponse;
import com.insurance.underwriting.service.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/underwriting")
@RequiredArgsConstructor
public class UnderwritingController {

    private final RiskScoringService scoringService;

    @PostMapping("/risk-score")
    public ResponseEntity<RiskScoreResponse> score(@Valid @RequestBody RiskScoreRequest request) {
        return ResponseEntity.ok(scoringService.score(request));
    }
}
