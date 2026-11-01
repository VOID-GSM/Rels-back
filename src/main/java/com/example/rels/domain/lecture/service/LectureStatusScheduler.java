package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;

@Component
public class LectureStatusScheduler {

	private final LectureRepository lectureRepository;
	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final LectureLifecycleHandler lifecycleHandler;

	public LectureStatusScheduler(LectureRepository lectureRepository,
								  LectureEnrollmentRepository lectureEnrollmentRepository,
								  LectureLifecycleHandler lifecycleHandler) {
		this.lectureRepository = lectureRepository;
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.lifecycleHandler = lifecycleHandler;
	}

	@Scheduled(fixedDelayString = "${rels.lecture.lifecycle-sync-delay-ms:60000}")
	@Transactional
	public void syncLectureStatuses() {
		LocalDateTime now = SchoolTime.now();
		// CLOSE는 마지막 상태라 다시 볼 필요가 없다.
		List<LectureEntity> lectures = lectureRepository.findAllByStatusNot(LectureStatus.CLOSE);
		if (lectures.isEmpty()) return;

		Map<Long, Long> enrolledCountByLectureId = getEnrolledCountByLectureId(lectures);
		for (LectureEntity lecture : lectures) {
			long enrolledCount = enrolledCountByLectureId.getOrDefault(lecture.getId(), 0L);
			lifecycleHandler.refreshLectureLifecycle(lecture, now, enrolledCount);
		}
	}

	private Map<Long, Long> getEnrolledCountByLectureId(List<LectureEntity> lectures) {
		List<Long> lectureIds = lectures.stream().map(LectureEntity::getId).toList();

		return lectureEnrollmentRepository.countEnrollmentsByLectureIds(lectureIds).stream()
				.filter(projection -> projection.getStatus() == EnrollmentStatus.ENROLLED)
				.collect(Collectors.toMap(
						LectureEnrollmentCountProjection::getLectureId,
						LectureEnrollmentCountProjection::getEnrollmentCount));
	}
}
