package com.example.rels.lecture.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

public record LectureSummaryResponse(
		Long lectureId,
		String title,
		String description,
		Long creatorId,
		String creatorName,
		String lectureStatus,
		Map<Integer, Integer> gradeCapacities,
		long enrolledCount,
		long waitingCount,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime createdAt) {
}

