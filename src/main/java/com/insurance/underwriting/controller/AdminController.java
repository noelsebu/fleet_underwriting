package com.insurance.underwriting.controller;

import com.insurance.underwriting.entity.UnderwritingRecord;
import com.insurance.underwriting.repository.UnderwritingRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UnderwritingRecordRepository recordRepository;

    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
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

        String customerId = String.format("CUST-%d-%04d", LocalDate.now().getYear(), record.getId());
        String policyNumber = String.format("Pol-%d-%d", LocalDate.now().getYear(), record.getId());

        record.setWorkflowStatus("POLICY_ISSUED");
        record.setCustomerId(customerId);
        record.setPolicyNumber(policyNumber);
        recordRepository.save(record);

        return "redirect:/admin/queue";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Long id) {
        UnderwritingRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));

        record.setWorkflowStatus("REJECTED");
        recordRepository.save(record);

        return "redirect:/admin/queue";
    }
}
