package com.example.rels.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.rels.domain.auth.service.RefreshTokenCleanupScheduler;
import com.example.rels.domain.auth.store.RefreshTokenEntity;
import com.example.rels.domain.auth.store.RefreshTokenRepository;

@SpringBootTest
class RefreshTokenCleanupSchedulerTest {

	@Autowired
	private RefreshTokenCleanupScheduler refreshTokenCleanupScheduler;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
	}

	@Test
	void deleteExpiredTokensKeepsValidTokens() {
		refreshTokenRepository.save(new RefreshTokenEntity(1L, "expired", LocalDateTime.now().minusMinutes(1)));
		refreshTokenRepository.save(new RefreshTokenEntity(1L, "valid", LocalDateTime.now().plusMinutes(1)));

		refreshTokenCleanupScheduler.deleteExpiredTokens();

		assertFalse(refreshTokenRepository.findByToken("expired").isPresent());
		assertTrue(refreshTokenRepository.findByToken("valid").isPresent());
	}
}
