package com.example.rels.domain.auth.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.auth.dto.OAuthSignInResponse;

import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.AuthorizationUrlBuilder;

@Service
public class DgOAuthFlowService {

	private static final Duration STATE_TTL = Duration.ofMinutes(5);

	private final DataGsmOAuthClient dataGsmOAuthClient;
	private final AuthService authService;
	private final Map<String, LoginState> stateStore = new ConcurrentHashMap<>();

	public DgOAuthFlowService(DataGsmOAuthClient dataGsmOAuthClient, AuthService authService) {
		this.dataGsmOAuthClient = dataGsmOAuthClient;
		this.authService = authService;
	}

	public URI createLoginRedirect(String redirectUri) {
		authService.assertAllowedRedirectUri(redirectUri);
		cleanupExpiredStates();

		String state = UUID.randomUUID().toString();
		AuthorizationUrlBuilder urlBuilder = dataGsmOAuthClient.createAuthorizationUrl(redirectUri)
				.state(state)
				.enablePkce();

		stateStore.put(state, new LoginState(redirectUri, urlBuilder.getCodeVerifier(), Instant.now().plus(STATE_TTL)));
		return URI.create(urlBuilder.build());
	}

	public OAuthSignInResponse completeLogin(String code, String state) {
		if (code == null || code.isBlank() || state == null || state.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code/state 값이 필요합니다.");
		}

		LoginState loginState = stateStore.remove(state);
		if (loginState == null || loginState.expiresAt().isBefore(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "state가 유효하지 않거나 만료되었습니다.");
		}

		return authService.signIn(code, loginState.redirectUri(), loginState.codeVerifier());
	}

	private void cleanupExpiredStates() {
		Instant now = Instant.now();
		stateStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
	}

	private record LoginState(String redirectUri, String codeVerifier, Instant expiresAt) {
	}
}

