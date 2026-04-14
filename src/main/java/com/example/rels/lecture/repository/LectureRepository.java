package com.example.rels.lecture.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.example.rels.lecture.entity.LectureEntity;

import jakarta.persistence.LockModeType;

public interface LectureRepository extends JpaRepository<LectureEntity, Long> {

	@EntityGraph(attributePaths = "creator")
	Page<LectureEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	@EntityGraph(attributePaths = "creator")
	List<LectureEntity> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LectureEntity l where l.id = :lectureId")
	Optional<LectureEntity> findByIdForUpdate(Long lectureId);
}

