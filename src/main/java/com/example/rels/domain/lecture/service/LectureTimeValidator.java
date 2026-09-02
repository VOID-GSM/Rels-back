package com.example.rels.domain.lecture.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LectureTimeValidator {

    public LocalDateTime calculateApplicationDeadline(LocalDate lectureDate) {
        if (lectureDate == null) {
            return null;
        }
        LocalDate startOfWeek = lectureDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousThursday = startOfWeek.minusDays(4);
        return LocalDateTime.of(previousThursday, LocalTime.of(23, 59, 59));
    }

    public void validateApplicationTime(LocalDateTime approvalTime, LocalDateTime deadline, LocalDateTime now) {
        LocalDateTime openTime = approvalTime.toLocalDate().atTime(16, 20);
        if (approvalTime.isAfter(openTime)) {
            openTime = openTime.plusDays(1);
        }

        if (now.isBefore(openTime)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수강 신청은 " + openTime.toLocalDate() + " 오후 4시 20분부터 가능합니다."
            );
        }

        if (deadline != null && now.isAfter(deadline)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신청 마감 시간이 지났습니다 (전주 목요일 23:59 마감).");
        }
    }
}