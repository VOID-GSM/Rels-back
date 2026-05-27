package com.example.rels.domain.lecture.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MyEnrolledLectureResponse(
		Long lectureId,
		String title,
		String lectureStatus,
		String enrollmentStatus,
		String creatorName,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime applicationDeadline,
		LocalDateTime requestedAt) {
}

