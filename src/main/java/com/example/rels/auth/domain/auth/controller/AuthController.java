package com.example.rels.auth.domain.auth.controller;

import java.net.URI;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.rels.auth.domain.auth.dto.CurrentUserResponse;
import com.example.rels.auth.domain.auth.dto.OAuthSignInRequest;
import com.example.rels.auth.domain.auth.dto.OAuthSignInResponse;
import com.example.rels.auth.domain.auth.service.AuthService;
import com.example.rels.auth.domain.auth.service.DgOAuthFlowService;
import com.example.rels.auth.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final DgOAuthFlowService dgOAuthFlowService;

	@GetMapping("/dg/start")
	public ResponseEntity<Void> startDgLogin(@RequestParam String redirectUri) {
		URI location = dgOAuthFlowService.createLoginRedirect(redirectUri);
		return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
	}

	@GetMapping("/dg/callback")
	public OAuthSignInResponse dgCallback(@RequestParam String code, @RequestParam String state) {
		return dgOAuthFlowService.completeLogin(code, state);
	}

	@PostMapping("/signin")
	public OAuthSignInResponse signIn(@Valid @RequestBody OAuthSignInRequest request) {
		return authService.signIn(request);
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
}



