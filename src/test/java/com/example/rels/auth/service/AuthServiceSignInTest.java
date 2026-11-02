package com.example.rels.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.rels.domain.auth.dto.OAuthSignInResponse;
import com.example.rels.domain.auth.service.AuthService;
import com.example.rels.domain.auth.store.RefreshTokenRepository;
import com.example.rels.domain.user.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;

import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.StudentRole;
import team.themoment.datagsm.sdk.oauth.model.TokenResponse;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

@SpringBootTest(properties = "rels.lecture.lifecycle-sync-delay-ms=3600000")
class AuthServiceSignInTest {

	private static final String REDIRECT_URI = "http://localhost/callback";

	@MockitoBean
	private DataGsmOAuthClient dataGsmOAuthClient;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private DataSource dataSource;

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void signInDoesNotHoldDbConnectionDuringExternalCalls() {
		AtomicInteger activeConnections = new AtomicInteger(-1);
		TokenResponse tokenResponse = mock(TokenResponse.class);
		UserInfo userInfo = mockStudentUserInfo();

		when(tokenResponse.getAccessToken()).thenReturn("access-token");
		when(dataGsmOAuthClient.exchangeCodeForToken(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			activeConnections.set(activeConnections());
			return tokenResponse;
		});
		when(dataGsmOAuthClient.getUserInfo("access-token")).thenReturn(userInfo);

		authService.signIn("code", REDIRECT_URI, "verifier");

		assertEquals(0, activeConnections.get());
	}

	@Test
	void signInSavesUserAndRefreshToken() {
		TokenResponse tokenResponse = mock(TokenResponse.class);
		UserInfo userInfo = mockStudentUserInfo();

		when(tokenResponse.getAccessToken()).thenReturn("access-token");
		when(dataGsmOAuthClient.exchangeCodeForToken(anyString(), anyString(), anyString())).thenReturn(tokenResponse);
		when(dataGsmOAuthClient.getUserInfo("access-token")).thenReturn(userInfo);

		OAuthSignInResponse response = authService.signIn("code", REDIRECT_URI, "verifier");

		assertTrue(userRepository.findByEmail("student@test.com").isPresent());
		assertTrue(refreshTokenRepository.findByToken(response.refreshToken()).isPresent());
	}

	private UserInfo mockStudentUserInfo() {
		Student student = mock(Student.class);
		when(student.getName()).thenReturn("student");
		when(student.getStudentNumber()).thenReturn(1101);
		when(student.getRole()).thenReturn(StudentRole.GENERAL_STUDENT);

		UserInfo userInfo = mock(UserInfo.class);
		when(userInfo.isStudent()).thenReturn(true);
		when(userInfo.getStudent()).thenReturn(student);
		when(userInfo.getEmail()).thenReturn("student@test.com");
		return userInfo;
	}

	private int activeConnections() throws SQLException {
		return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
	}
}
