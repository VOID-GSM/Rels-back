package com.example.rels.lecture.dto;

import java.time.LocalDateTime;

public record EnrollmentApplicantResponse(
		Long userId,
		String name,
		String studentNumber,
		LocalDateTime requestedAt) {
}

