package com.example.rels.domain.auth.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.rels.domain.auth.store.RefreshTokenRepository;

@Component
public class RefreshTokenCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

	private final RefreshTokenRepository refreshTokenRepository;

	public RefreshTokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Scheduled(cron = "${rels.auth.refresh-token-cleanup-cron:0 0 4 * * *}")
	@Transactional
	public void deleteExpiredTokens() {
		int deletedCount = refreshTokenRepository.deleteExpired(LocalDateTime.now());
		log.info("만료된 리프레시 토큰 {}건 삭제", deletedCount);
	}
}
