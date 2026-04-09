package com.example.rels.auth.domain.auth.dto;

public record OAuthSignInResponse(
		String accessToken,
		Long userId,
		String email,
		String name,
		String studentNumber,
		String role) {
}

