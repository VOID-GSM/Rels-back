package com.example.rels.domain.lecture.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LectureTimeValidator {

    public void validateApplicationDeadline(LocalDate lectureDate, LocalTime lectureTime, LocalDateTime applicationDeadline) {
        if (lectureDate == null || lectureTime == null || applicationDeadline == null) {
            return;
        }

        LocalDateTime lectureStartDateTime = LocalDateTime.of(lectureDate, lectureTime);

        if (applicationDeadline.isAfter(lectureStartDateTime) || applicationDeadline.isEqual(lectureStartDateTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 마감 시간은 강연 시작 일시보다 이전이어야 합니다."
            );
        }
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
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수강 신청 마감 시간이 지났습니다."
            );
        }
    }
}