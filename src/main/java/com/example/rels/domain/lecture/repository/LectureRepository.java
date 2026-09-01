package com.example.rels.domain.lecture.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.LectureEntity;

import jakarta.persistence.LockModeType;

public interface LectureRepository extends JpaRepository<LectureEntity, Long> {

	@EntityGraph(attributePaths = {"creator", "speakers"})
	Page<LectureEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	@EntityGraph(attributePaths = {"creator", "speakers"})
	Page<LectureEntity> findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus approvalStatus, Pageable pageable);

	@EntityGraph(attributePaths = {"creator", "speakers"})
	@Query("select distinct l from LectureEntity l left join l.speakers s where l.approvalStatus = :approvalStatus or l.creator.id = :userId or s.id = :userId")
	Page<LectureEntity> findVisibleToUser(ApprovalStatus approvalStatus, Long userId, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from LectureEntity l where l.id = :lectureId")
	Optional<LectureEntity> findByIdForUpdate(Long lectureId);

	@EntityGraph(attributePaths = {"creator", "speakers"})
	@Query("select distinct l from LectureEntity l join l.speakers s where s.id = :userId order by l.createdAt desc")
	List<LectureEntity> findAllBySpeakerIdOrderByCreatedAtDesc(Long userId);
}
