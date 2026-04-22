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
		long enrolledCount,
		long waitingCount,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime createdAt,
		Map<Integer, Integer> capacityByGrade // 추가: 학년별 정원
) {
}
