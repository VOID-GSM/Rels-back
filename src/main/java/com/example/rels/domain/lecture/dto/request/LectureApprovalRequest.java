package com.example.rels.domain.lecture.dto.request;

import com.example.rels.domain.lecture.entity.ApprovalStatus;
import jakarta.validation.constraints.NotNull;

public record LectureApprovalRequest(
        @NotNull ApprovalStatus approvalStatus,
        String rejectionReason
) {}