package com.example.rels.domain.auth.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MyCreatedLectureResponse(
		Long lectureId,
		String title,
		String lectureStatus,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime applicationDeadline,
		LocalDateTime createdAt) {
}

