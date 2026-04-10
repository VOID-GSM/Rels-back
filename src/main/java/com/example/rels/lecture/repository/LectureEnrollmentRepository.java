package com.example.rels.lecture.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.rels.lecture.entity.EnrollmentStatus;
import com.example.rels.lecture.entity.LectureEnrollmentEntity;

public interface LectureEnrollmentRepository extends JpaRepository<LectureEnrollmentEntity, Long> {

	Optional<LectureEnrollmentEntity> findByLectureIdAndUserId(Long lectureId, Long userId);

	long countByLectureIdAndStatus(Long lectureId, EnrollmentStatus status);

	Optional<LectureEnrollmentEntity> findFirstByLectureIdAndStatusOrderByRequestedAtAscIdAsc(Long lectureId,
			EnrollmentStatus status);
}

