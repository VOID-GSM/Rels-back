package com.example.rels.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.rels.domain.user.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByEmail(String email);

	/** 연사자 검색. 이름 일부 또는 학번 일부로 찾고, 학번 순으로 20명까지 준다. */
	List<UserEntity> findTop20ByNameContainingIgnoreCaseOrStudentNumberContainingOrderByStudentNumberAsc(
			String name, String studentNumber);
}

