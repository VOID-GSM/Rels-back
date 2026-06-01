package com.example.rels.domain.lecture.dto;

import java.time.LocalDateTime;

public record EnrollmentUserResponse(
		Long userId,
		String name,
		String studentNumber,
		LocalDateTime requestedAt
) {}

