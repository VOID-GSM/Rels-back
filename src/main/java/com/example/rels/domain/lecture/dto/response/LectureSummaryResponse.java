package com.example.rels.domain.lecture.dto.response;

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
		String creatorStudentNumber,
		String lectureStatus,
		String approvalStatus,
		String rejectionReason,
		long enrolledCount,
		long waitingCount,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime applicationDeadline,
		LocalDateTime createdAt,
		LocalDateTime approvedAt,
		Map<Integer, Integer> capacityByGrade,
		Integer totalCapacity
) {
}
