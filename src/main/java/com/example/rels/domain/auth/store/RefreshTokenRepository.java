package com.example.rels.domain.auth.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

	Optional<RefreshTokenEntity> findByToken(String token);

	void deleteByUserId(Long userId);

	void deleteByToken(String token);

	@Modifying
	@Query("delete from RefreshTokenEntity t where t.expiresAt < :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
