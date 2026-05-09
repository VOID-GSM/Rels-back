package com.example.rels.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthSignInRequest(
		@NotBlank String authCode,
		@NotBlank String redirectUri,
		@NotBlank String codeVerifier) {
}

