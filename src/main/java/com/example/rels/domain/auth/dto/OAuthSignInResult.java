package com.example.rels.domain.auth.dto;

public record OAuthSignInResult(
		String accessToken,
		String refreshToken,
		Long userId,
		String email,
		String name,
		String studentNumber,
		String role) {
}

