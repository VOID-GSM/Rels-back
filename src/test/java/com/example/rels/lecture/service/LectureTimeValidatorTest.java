package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.service.LectureTimeValidator;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;

class LectureTimeValidatorTest {

    private final LectureTimeValidator validator = new LectureTimeValidator();

    @Test
    void approvalBeforeFourTwentyOpensSameDayAtFourTwenty() {
        LocalDateTime openTime = validator.defaultOpenTime(LocalDateTime.of(2026, 9, 2, 15, 0));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateOpenTimeReached(openTime, LocalDateTime.of(2026, 9, 2, 16, 19)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-02 오후 4시 20분부터 가능합니다.", exception.getReason());
    }

    @Test
    void approvalAfterFourTwentyOpensNextDayAtFourTwenty() {
        LocalDateTime openTime = validator.defaultOpenTime(LocalDateTime.of(2026, 9, 2, 16, 21));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateOpenTimeReached(openTime, LocalDateTime.of(2026, 9, 3, 16, 19)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-03 오후 4시 20분부터 가능합니다.", exception.getReason());
    }

    @Test
    void customOpenTimeTakesPriorityOverFourTwentyRule() {
        LectureEntity lecture = lecture();
        LocalDateTime openAt = LocalDateTime.of(2026, 9, 2, 18, 30);
        lecture.updateApplicationOpenAt(openAt);

        assertEquals(openAt, validator.resolveApplicationOpenTime(lecture));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateApplicationOpen(lecture, openAt.minusMinutes(1)));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("수강 신청은 2026-09-02 오후 6시 30분부터 가능합니다.", exception.getReason());
        assertDoesNotThrow(() -> validator.validateApplicationOpen(lecture, openAt));
    }

    @Test
    void withoutCustomOpenTimeFallsBackToFourTwentyAfterApproval() {
        LectureEntity lecture = lecture();
        // 서버 시각은 UTC다. 2026-09-02 05:00 UTC = 14:00 KST.
        setField(lecture, "approvedAt", LocalDateTime.of(2026, 9, 2, 5, 0));

        assertEquals(LocalDateTime.of(2026, 9, 2, 16, 20), validator.resolveApplicationOpenTime(lecture));
    }

    @Test
    void newOpenTimeMustBeInTheFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 15, 0);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.validateNewApplicationOpenAt(now, now));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertDoesNotThrow(() -> validator.validateNewApplicationOpenAt(now.plusMinutes(1), now));
        assertDoesNotThrow(() -> validator.validateNewApplicationOpenAt(null, now));
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

    private LectureEntity lecture() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        return TestEntityFactory.createLecture("title", "description", creator, "장소",
                LocalDate.of(2026, 9, 10), LocalTime.NOON, LocalDateTime.of(2026, 9, 9, 23, 59), 20, 1L);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
