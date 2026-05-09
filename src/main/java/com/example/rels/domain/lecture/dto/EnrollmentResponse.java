package com.example.rels.domain.lecture.dto;

public record EnrollmentResponse(
		Long lectureId,
		String enrollmentStatus,
		long enrolledCount,
		long waitingCount) {
}

