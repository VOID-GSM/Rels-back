package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.service.LectureTimeValidator;

class LectureTimeValidatorTest {

	private final LectureTimeValidator validator = new LectureTimeValidator();

	@Test
	void resolveApplicationDeadlineKeepsRequestedDeadline() {
		LocalDate lectureDate = LocalDate.of(2026, 9, 10);
		LocalTime lectureTime = LocalTime.of(16, 30);
		LocalDateTime requested = LocalDateTime.of(2026, 9, 9, 18, 0);

		assertEquals(requested, validator.resolveApplicationDeadline(requested, lectureDate, lectureTime));
	}

	@Test
	void resolveApplicationDeadlineFallsBackToPreviousThursdayWhenOmitted() {
		LocalDate lectureDate = LocalDate.of(2026, 9, 10);
		LocalTime lectureTime = LocalTime.of(16, 30);

		LocalDateTime deadline = validator.resolveApplicationDeadline(null, lectureDate, lectureTime);

		assertEquals(LocalDateTime.of(2026, 9, 3, 23, 59, 59), deadline);
	}

	@Test
	void resolveApplicationDeadlineRejectsDeadlineAtOrAfterLectureStart() {
		LocalDate lectureDate = LocalDate.of(2026, 9, 10);
		LocalTime lectureTime = LocalTime.of(16, 30);
		LocalDateTime requested = LocalDateTime.of(2026, 9, 10, 16, 30);

		var exception = assertThrows(ResponseStatusException.class,
				() -> validator.resolveApplicationDeadline(requested, lectureDate, lectureTime));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}
}
