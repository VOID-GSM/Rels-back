package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.service.LectureTimeValidator;

class LectureTimeValidatorTest {

    private final LectureTimeValidator validator = new LectureTimeValidator();

    @Test
    void approvalBeforeFourTwentyOpensSameDayAtFourTwenty() {
        LocalDateTime approval = LocalDateTime.of(2026, 9, 2, 15, 0);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateApplicationTime(approval, LocalDateTime.of(2026, 9, 2, 16, 19)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-02 오후 4시 20분부터 가능합니다.", exception.getReason());
    }

    @Test
    void approvalAfterFourTwentyOpensNextDayAtFourTwenty() {
        LocalDateTime approval = LocalDateTime.of(2026, 9, 2, 16, 21);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateApplicationTime(approval, LocalDateTime.of(2026, 9, 3, 16, 19)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-03 오후 4시 20분부터 가능합니다.", exception.getReason());
    }

    @Test
    void customOpenTimeBlocksEarlierEnrollment() {
        LocalDateTime openAt = LocalDateTime.of(2026, 9, 2, 18, 30);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateCustomApplicationTime(openAt, LocalDateTime.of(2026, 9, 2, 18, 29)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-02 18:30부터 가능합니다.", exception.getReason());
    }

    @Test
    void customOpenTimeAllowsEnrollmentFromThatMoment() {
        LocalDateTime openAt = LocalDateTime.of(2026, 9, 2, 18, 30);

        assertDoesNotThrow(() -> validator.validateCustomApplicationTime(openAt, openAt));
    }

    @Test
    void openTimeMustComeBeforeDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 3, 23, 59);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateApplicationOpenAt(deadline, deadline));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertDoesNotThrow(() -> validator.validateApplicationOpenAt(deadline.minusMinutes(1), deadline));
        assertDoesNotThrow(() -> validator.validateApplicationOpenAt(null, deadline));
    }
}
