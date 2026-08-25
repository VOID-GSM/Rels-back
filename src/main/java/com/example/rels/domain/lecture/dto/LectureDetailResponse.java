package com.example.rels.domain.lecture.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;



public record LectureDetailResponse(
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
		String myEnrollmentStatus,
		String lectureLocation,
		LocalDate lectureDate,
		LocalTime lectureTime,
		LocalDateTime applicationDeadline,
		LocalDateTime createdAt,
		Map<Integer, Integer> capacityByGrade,
		Integer totalCapacity
) {
}

