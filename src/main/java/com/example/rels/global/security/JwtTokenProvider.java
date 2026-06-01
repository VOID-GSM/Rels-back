package com.example.rels.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.rels.domain.user.entity.UserEntity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private final SecretKey secretKey;
	private final long validityInMinutes;
	private final long refreshValidityInMinutes;

	public JwtTokenProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long validityInMinutes, @Value("${jwt.refresh-expiration:10080}") long refreshValidityInMinutes) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.validityInMinutes = validityInMinutes;
		this.refreshValidityInMinutes = refreshValidityInMinutes;
	}

	public String createToken(UserEntity user) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + validityInMinutes * 60 * 1000);

		return Jwts.builder()
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("name", user.getName())
				.claim("studentNumber", user.getStudentNumber())
				.claim("role", user.getRole().name())
				.issuedAt(now)
				.expiration(expiration)
				.signWith(secretKey)
				.compact();
	}

	public String createRefreshToken(UserEntity user) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + refreshValidityInMinutes * 60 * 1000);

		return Jwts.builder()
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiration(expiration)
				.signWith(secretKey)
				.compact();
	}

	public Claims parseClaims(String token) {
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
	}

	public Long getUserIdFromToken(String token) {
		return Long.parseLong(parseClaims(token).getSubject());
	}

	public boolean isTokenExpired(String token) {
		try {
			parseClaims(token);
			return false;
		} catch (Exception e) {
			return true;
		}
	}
}

