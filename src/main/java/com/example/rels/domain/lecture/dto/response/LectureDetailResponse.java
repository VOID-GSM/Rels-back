package com.example.rels.domain.lecture.dto.response;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.List;



public record LectureDetailResponse(
		Long lectureId,
		String title,
		String description,
		Long creatorId,
		String creatorName,
		String creatorStudentNumber,
		List<LectureSpeakerResponse> speakers,
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
		LocalDateTime approvedAt,
		Map<Integer, Integer> capacityByGrade,
		Integer totalCapacity
) {
}

