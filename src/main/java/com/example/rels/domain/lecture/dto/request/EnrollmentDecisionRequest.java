package com.example.rels.domain.lecture.dto.request;

import jakarta.validation.constraints.NotNull;

public record EnrollmentDecisionRequest(
		@NotNull Boolean approved
) {
}
