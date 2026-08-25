package com.example.rels.domain.lecture.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.rels.domain.lecture.entity.LectureEntity;

import jakarta.persistence.LockModeType;

public interface LectureRepository extends JpaRepository<LectureEntity, Long> {

	@EntityGraph(attributePaths = "creator")
	Page<LectureEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	/**
	 * 승인된 강의 + 요청자 본인이 개설한 강의(승인 대기/거절 포함)를 함께 조회한다.
	 * viewerId가 null이면 승인된 강의만 조회된다.
	 */
	@EntityGraph(attributePaths = "creator")
	@Query("select l from LectureEntity l"
			+ " where l.approvalStatus is null"
			+ " or l.approvalStatus = com.example.rels.domain.lecture.entity.ApprovalStatus.APPROVED"
			+ " or l.creator.id = :viewerId")
	Page<LectureEntity> findAllVisibleTo(@Param("viewerId") Long viewerId, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LectureEntity l where l.id = :lectureId")
	Optional<LectureEntity> findByIdForUpdate(Long lectureId);

	List<LectureEntity> findAllByCreatorIdOrderByCreatedAtDesc(Long creatorId);
}

