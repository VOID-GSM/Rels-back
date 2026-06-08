package com.example.rels.domain.auth.service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.auth.dto.OAuthSignInRequest;
import com.example.rels.domain.auth.dto.OAuthSignInResponse;
import com.example.rels.domain.auth.dto.RefreshTokenRequest;
import com.example.rels.domain.auth.store.RefreshTokenEntity;
import com.example.rels.domain.auth.store.RefreshTokenRepository;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.global.security.JwtTokenProvider;

import io.jsonwebtoken.Claims;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.TokenResponse;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final DataGsmOAuthClient dataGsmOAuthClient;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenRepository refreshTokenRepository;

	@Value("${oauth.datagsm.redirect-uris}")
	private Set<String> allowedRedirectUris;

	@Value("${jwt.refresh-expiration:10080}")
	private long refreshTokenValidityInMinutes;

	public AuthService(DataGsmOAuthClient dataGsmOAuthClient, UserRepository userRepository,
			JwtTokenProvider jwtTokenProvider, RefreshTokenRepository refreshTokenRepository) {
		this.dataGsmOAuthClient = dataGsmOAuthClient;
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Transactional
	public OAuthSignInResponse signIn(OAuthSignInRequest request) {
		return signIn(request.authCode(), request.redirectUri(), request.codeVerifier());
	}

	@Transactional
	public OAuthSignInResponse signIn(String authCode, String redirectUri, String codeVerifier) {
		assertAllowedRedirectUri(redirectUri);

		try {
			TokenResponse tokenResponse = dataGsmOAuthClient.exchangeCodeForToken(
					authCode,
					redirectUri,
					codeVerifier);
			UserInfo userInfo = dataGsmOAuthClient.getUserInfo(tokenResponse.getAccessToken());

			if (!userInfo.isStudent()) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생만 로그인할 수 있습니다.");
			}

			Student student = userInfo.getStudent();
			if (student == null || student.getName() == null || student.getStudentNumber() == null || userInfo.getEmail() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학생 정보가 유효하지 않습니다.");
			}

			team.themoment.datagsm.sdk.oauth.model.StudentRole studentRole = student.getRole();
			UserEntity user = findOrCreateUser(userInfo.getEmail(), student.getName(), String.valueOf(student.getStudentNumber()), studentRole);
			
			String accessToken = jwtTokenProvider.createToken(user);
			String refreshToken = jwtTokenProvider.createRefreshToken(user);
			
			LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(refreshTokenValidityInMinutes);
			RefreshTokenEntity savedRefreshToken = refreshTokenRepository.save(
					new RefreshTokenEntity(user.getId(), refreshToken, expiresAt));

			return new OAuthSignInResponse(
					accessToken,
					savedRefreshToken.getToken(),
					user.getId(),
					user.getEmail(),
					user.getName(),
					user.getStudentNumber(),
					user.getRole().name());
		} catch (DataGsmException e) {
			log.warn("DataGSM OAuth error: status={}, message={}", e.getStatusCode(), e.getMessage());
			throw new ResponseStatusException(resolveStatus(e.getStatusCode()), "OAuth 인증에 실패했습니다.");
		}
	}

	@Transactional
	public OAuthSignInResponse refresh(RefreshTokenRequest request) {
		String refreshToken = request.refreshToken();
		
		try {
			jwtTokenProvider.parseClaims(refreshToken);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
		}

		RefreshTokenEntity refreshTokenEntity = refreshTokenRepository.findByToken(refreshToken)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "등록되지 않은 리프레시 토큰입니다."));

		if (refreshTokenEntity.isExpired()) {
			refreshTokenRepository.delete(refreshTokenEntity);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다.");
		}

		UserEntity user = userRepository.findById(refreshTokenEntity.getUserId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

		String newAccessToken = jwtTokenProvider.createToken(user);
		String newRefreshToken = jwtTokenProvider.createRefreshToken(user);

		refreshTokenRepository.delete(refreshTokenEntity);
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(refreshTokenValidityInMinutes);
		RefreshTokenEntity newRefreshTokenEntity = refreshTokenRepository.save(
				new RefreshTokenEntity(user.getId(), newRefreshToken, expiresAt));

		return new OAuthSignInResponse(
				newAccessToken,
				newRefreshTokenEntity.getToken(),
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getStudentNumber(),
				user.getRole().name());
	}

	@Transactional
	public void logout(RefreshTokenRequest request) {
		refreshTokenRepository.deleteByToken(request.refreshToken());
	}

	private HttpStatus resolveStatus(int statusCode) {
		HttpStatus resolved = HttpStatus.resolve(statusCode);
		return resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
	}

	public void assertAllowedRedirectUri(String redirectUri) {
		if (redirectUri == null || redirectUri.isBlank() || !allowedRedirectUris.contains(redirectUri)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "허용되지 않은 redirectUri입니다.");
		}
	}

	@Transactional(readOnly = true)
	public UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
	}

	private UserEntity findOrCreateUser(String email, String name, String studentNumber, team.themoment.datagsm.sdk.oauth.model.StudentRole studentRole) {
		Role role = studentRole == team.themoment.datagsm.sdk.oauth.model.StudentRole.STUDENT_COUNCIL ? Role.ADMIN : Role.USER;
		return userRepository.findByEmail(email)
				.map(existing -> {
					existing.updateProfile(name, studentNumber, role);
					return existing;
				})
				.orElseGet(() -> userRepository.save(new UserEntity(email, name, studentNumber, role)));
	}
}

