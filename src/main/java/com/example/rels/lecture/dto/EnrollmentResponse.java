package com.example.rels.lecture.dto;

public record EnrollmentResponse(
		Long lectureId,
		String enrollmentStatus,
		long enrolledCount,
		long waitingCount) {
}

