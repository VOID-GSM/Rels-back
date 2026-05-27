package com.example.rels.domain.lecture.dto;

import java.time.LocalDateTime;

public record EnrollmentResponse(
		Long lectureId,
		String enrollmentStatus,
		long enrolledCount,
		long waitingCount,
		LocalDateTime requestedAt) {
}

