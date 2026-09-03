package com.example.rels.domain.user.dto;

public record UserSummaryResponse(
		Long userId,
		String name,
		String studentNumber
) {
}
