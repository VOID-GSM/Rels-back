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

    public void validateApplicationOpenAt(LocalDateTime applicationOpenAt, LocalDateTime applicationDeadline) {
        if (applicationOpenAt == null || applicationDeadline == null) {
            return;
        }

        if (!applicationOpenAt.isBefore(applicationDeadline)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 시작 시각은 신청 마감 시각보다 이전이어야 합니다."
            );
        }
    }

    /**
     * 강연자가 신청 시작 시각을 정해 둔 강연은 그 시각부터 받는다.
     * 승인 여부는 LectureEnrollmentService.enroll이 따로 본다.
     */
    public void validateCustomApplicationTime(LocalDateTime applicationOpenAt, LocalDateTime now) {
        if (now.isBefore(applicationOpenAt)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수강 신청은 " + applicationOpenAt.toLocalDate() + " "
                            + String.format("%02d:%02d", applicationOpenAt.getHour(), applicationOpenAt.getMinute())
                            + "부터 가능합니다."
            );
        }
    }

    /**
     * 신청이 열리는 시각만 본다. 승인한 날 16:20부터이고, 승인이 이미 16:20을 넘겼으면 다음 날 16:20부터다.
     *
     * 마감 시각은 여기서 막지 않는다. 마감 뒤에도 대기 신청은 받기 때문이다.
     * 마감 뒤에 들어온 신청을 대기로 돌리는 일은 LectureService.enroll이 맡는다.
     */
    public void validateApplicationTime(LocalDateTime approvalTime, LocalDateTime now) {
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
    }
}