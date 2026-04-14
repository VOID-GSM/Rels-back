package com.example.rels.auth.domain.auth.dto;

import java.util.List;

public record CurrentUserResponse(
		Long userId,
		String email,
		String name,
		String studentNumber,
		String role,
		List<MyCreatedLectureResponse> createdLectures,
		List<MyEnrolledLectureResponse> enrolledLectures) {
}

