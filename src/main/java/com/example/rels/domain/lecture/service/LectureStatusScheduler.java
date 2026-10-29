package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureRepository;

@Component
public class LectureStatusScheduler {

	private final LectureRepository lectureRepository;
	private final LectureLifecycleHandler lifecycleHandler;

	public LectureStatusScheduler(LectureRepository lectureRepository, LectureLifecycleHandler lifecycleHandler) {
		this.lectureRepository = lectureRepository;
		this.lifecycleHandler = lifecycleHandler;
	}

	@Scheduled(fixedDelayString = "${rels.lecture.lifecycle-sync-delay-ms:60000}")
	@Transactional
	public void syncLectureStatuses() {
		LocalDateTime now = SchoolTime.now();
		List<LectureEntity> lectures = lectureRepository.findAll();
		for (LectureEntity lecture : lectures) {
			lifecycleHandler.refreshLectureLifecycle(lecture, now);
		}
	}
}
