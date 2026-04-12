package com.example.rels.lecture.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record LectureDetailResponse(
		Long lectureId,
		String title,
		String description,
		Long creatorId,
		String creatorName,
		String lectureStatus,
		long enrolledCount,
		long waitingCount,
		String myEnrollmentStatus,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime createdAt) {
}

