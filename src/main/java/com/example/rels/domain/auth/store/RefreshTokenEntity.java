package com.example.rels.domain.auth.store;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private String token;
	private LocalDateTime expiresAt;
	private LocalDateTime createdAt;

	public RefreshTokenEntity(Long userId, String token, LocalDateTime expiresAt) {
		this.userId = userId;
		this.token = token;
		this.expiresAt = expiresAt;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isExpired() {
		return LocalDateTime.now().isAfter(expiresAt);
	}
}

