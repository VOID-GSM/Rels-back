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

    /**
     * 개설자가 입력한 신청 마감 시각을 확정합니다.
     *
     * 비워서 보내면 예전처럼 강연 전 주 목요일 23:59:59로 계산합니다. 디스코드 봇처럼
     * 마감을 보내지 않는 클라이언트가 그대로 동작하도록 남겨 둔 기본값입니다.
     */
    public LocalDateTime resolveApplicationDeadline(LocalDateTime requestedDeadline, LocalDate lectureDate, LocalTime lectureTime) {
        if (requestedDeadline == null) {
            return calculateApplicationDeadline(lectureDate);
        }

        if (lectureDate != null && lectureTime != null) {
            LocalDateTime lectureStart = LocalDateTime.of(lectureDate, lectureTime);
            if (!requestedDeadline.isBefore(lectureStart)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "신청 마감은 강연 시작 전이어야 합니다."
                );
            }
        }

        return requestedDeadline;
    }

    public void validateApplicationTime(LocalDateTime createdAt, LocalDateTime deadline, LocalDateTime now) {
        LocalDateTime openTime = createdAt.toLocalDate().atTime(16, 20);
        if (!createdAt.isBefore(openTime)) {
            openTime = openTime.plusDays(1);
        }

        if (now.isBefore(openTime)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수강 신청은 " + openTime.toLocalDate() + " 오후 4시 20분부터 가능합니다."
            );
        }

    }
}
