package com.example.rels.domain.auth.dto;

public record OAuthSignInResponse(
		String accessToken,
		String refreshToken,
		Long userId,
		String email,
		String name,
		String studentNumber,
		String role) {
}

