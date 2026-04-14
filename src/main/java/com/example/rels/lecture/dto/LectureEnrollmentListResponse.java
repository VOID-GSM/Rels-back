package com.example.rels.lecture.dto;

import java.util.List;

public record LectureEnrollmentListResponse(
		Long lectureId,
		List<EnrollmentApplicantResponse> enrolledApplicants,
		List<EnrollmentApplicantResponse> waitingApplicants) {
}

