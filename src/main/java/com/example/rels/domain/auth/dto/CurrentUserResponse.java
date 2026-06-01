package com.example.rels.domain.auth.dto;

public record CurrentUserResponse(
		Long userId,
		String email,
		String name,
		String studentNumber,
		String role) {
}

