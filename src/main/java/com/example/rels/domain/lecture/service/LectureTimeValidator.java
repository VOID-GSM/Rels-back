package com.example.rels.domain.lecture.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.entity.LectureEntity;

@Component
public class LectureTimeValidator {

    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(16, 20);
    private static final DateTimeFormatter OPEN_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd a h'시' mm'분'", Locale.KOREAN);

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
     * 새로 정하는 신청 시작 시각은 학교 시각(KST) 기준으로 미래여야 한다.
     * 요청 DTO의 @Future는 JVM 기본 시간대로 비교해서 서버가 UTC면 9시간 어긋나므로 여기서 본다.
     */
    public void validateNewApplicationOpenAt(LocalDateTime applicationOpenAt, LocalDateTime now) {
        if (applicationOpenAt != null && !applicationOpenAt.isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "신청 시작 시각은 현재보다 미래여야 합니다."
            );
        }
    }

    /**
     * 신청이 실제로 열리는 시각. 강연자가 정해 뒀으면 그 시각이고,
     * 아니면 승인한 날 16:20(승인이 16:20을 넘겼으면 다음 날 16:20)이다.
     */
    public LocalDateTime resolveApplicationOpenTime(LectureEntity lecture) {
        if (lecture.getApplicationOpenAt() != null) {
            return lecture.getApplicationOpenAt();
        }
        LocalDateTime approvedAt = lecture.getApprovedAt() != null ? lecture.getApprovedAt() : lecture.getCreatedAt();
        return defaultOpenTime(SchoolTime.fromServerTime(approvedAt));
    }

    public LocalDateTime defaultOpenTime(LocalDateTime approvalTime) {
        LocalDateTime openTime = approvalTime.toLocalDate().atTime(DEFAULT_OPEN_TIME);
        return approvalTime.isAfter(openTime) ? openTime.plusDays(1) : openTime;
    }

    /**
     * 신청이 열렸는지만 본다. 승인 여부는 LectureEnrollmentService.enroll이 따로 본다.
     *
     * 마감 시각은 여기서 막지 않는다. 마감 뒤에도 대기 신청은 받기 때문이다.
     * 마감 뒤에 들어온 신청을 대기로 돌리는 일은 LectureEnrollmentService.enroll이 맡는다.
     */
    public void validateApplicationOpen(LectureEntity lecture, LocalDateTime now) {
        validateOpenTimeReached(resolveApplicationOpenTime(lecture), now);
    }

    public void validateOpenTimeReached(LocalDateTime openTime, LocalDateTime now) {
        if (now.isBefore(openTime)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수강 신청은 " + openTime.format(OPEN_TIME_FORMAT) + "부터 가능합니다."
            );
        }
    }
}
