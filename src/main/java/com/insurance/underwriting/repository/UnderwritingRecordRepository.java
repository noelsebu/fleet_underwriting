package com.insurance.underwriting.repository;

import com.insurance.underwriting.entity.UnderwritingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnderwritingRecordRepository extends JpaRepository<UnderwritingRecord, Long> {
    List<UnderwritingRecord> findAllByOrderBySubmittedAtDesc();
    List<UnderwritingRecord> findByWorkflowStatusOrderBySubmittedAtDesc(String workflowStatus);
}
