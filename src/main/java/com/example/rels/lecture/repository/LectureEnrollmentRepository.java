package com.example.rels.lecture.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.rels.lecture.entity.EnrollmentStatus;
import com.example.rels.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.lecture.repository.LectureEnrollmentCountProjection;

public interface LectureEnrollmentRepository extends JpaRepository<LectureEnrollmentEntity, Long> {

	Optional<LectureEnrollmentEntity> findByLectureIdAndUserId(Long lectureId, Long userId);

	long countByLectureIdAndStatus(Long lectureId, EnrollmentStatus status);

	@Query("""
			select e.lecture.id as lectureId, e.status as status, count(e) as enrollmentCount
			from LectureEnrollmentEntity e
			where e.lecture.id in :lectureIds
			group by e.lecture.id, e.status
			""")
	List<LectureEnrollmentCountProjection> countEnrollmentsByLectureIds(Collection<Long> lectureIds);

	Optional<LectureEnrollmentEntity> findFirstByLectureIdAndStatusOrderByRequestedAtAscIdAsc(Long lectureId,
			EnrollmentStatus status);
}

