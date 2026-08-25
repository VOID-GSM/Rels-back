package com.example.rels.domain.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LectureRejectRequest(
	@NotBlank @Size(max = 500) String rejectionReason
) {
}
