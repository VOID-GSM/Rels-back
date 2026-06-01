package com.example.rels.domain.auth.controller;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.rels.domain.auth.dto.CurrentUserResponse;
import com.example.rels.domain.auth.dto.OAuthSignInRequest;
import com.example.rels.domain.auth.dto.OAuthSignInResponse;
import com.example.rels.domain.auth.dto.OAuthSignInResult;
import com.example.rels.domain.auth.service.AuthService;
import com.example.rels.domain.auth.service.DgOAuthFlowService;
import com.example.rels.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

	private final AuthService authService;
	private final DgOAuthFlowService dgOAuthFlowService;

	@Value("${jwt.refresh-expiration:10080}")
	private long refreshTokenValidityInMinutes;

	@Value("${app.cookie.secure:false}")
	private boolean secureCookie;

	@Value("${app.cookie.same-site:Lax}")
	private String cookieSameSite;

	@GetMapping("/dg/start")
	public ResponseEntity<Void> startDgLogin(@RequestParam String redirectUri) {
		URI location = dgOAuthFlowService.createLoginRedirect(redirectUri);
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}

	@GetMapping("/dg/callback")
	public ResponseEntity<OAuthSignInResponse> dgCallback(@RequestParam String code, @RequestParam String state) {
		OAuthSignInResult result = dgOAuthFlowService.completeLogin(code, state);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
				.body(toResponse(result));
	}

	@PostMapping("/signin")
	public ResponseEntity<OAuthSignInResponse> signIn(@Valid @RequestBody OAuthSignInRequest request) {
		OAuthSignInResult result = authService.signIn(request);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
				.body(toResponse(result));
	}

	@PostMapping("/refresh")
	public ResponseEntity<OAuthSignInResponse> refresh(
			@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 필요합니다.");
		}

		OAuthSignInResult result = authService.refresh(refreshToken);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.refreshToken()).toString())
				.body(toResponse(result));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			authService.logout(refreshToken);
		}
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie().toString())
				.build();
	}

	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
		if (currentUser == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다.");
		}

		return new CurrentUserResponse(
				currentUser.userId(),
				currentUser.email(),
				currentUser.name(),
				currentUser.studentNumber(),
				currentUser.role().name());
	}

	private OAuthSignInResponse toResponse(OAuthSignInResult result) {
		return new OAuthSignInResponse(
				result.accessToken(),
				result.userId(),
				result.email(),
				result.name(),
				result.studentNumber(),
				result.role());
	}

	private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
				.httpOnly(true)
				.secure(secureCookie)
				.path("/")
				.sameSite(cookieSameSite)
				.maxAge(Duration.ofMinutes(refreshTokenValidityInMinutes))
				.build();
	}

	private ResponseCookie clearRefreshTokenCookie() {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(secureCookie)
				.path("/")
				.sameSite(cookieSameSite)
				.maxAge(Duration.ZERO)
				.build();
	}
}



