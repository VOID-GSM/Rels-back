package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;

@Component
public class LectureLifecycleHandler {

    private static final long CONFIRM_THRESHOLD = 10;
    private final LectureEnrollmentRepository lectureEnrollmentRepository;

    public LectureLifecycleHandler(LectureEnrollmentRepository lectureEnrollmentRepository) {
        this.lectureEnrollmentRepository = lectureEnrollmentRepository;
    }

    public void promoteFirstWaitingUser(LectureEntity lecture, LocalDateTime now) {
        Long lectureId = lecture.getId();
        List<LectureEnrollmentEntity> enrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

        List<LectureEnrollmentEntity> enrolled = enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                .toList();

        int capacity = resolveTotalCapacity(lecture);
        if (capacity > 0 && enrolled.size() >= capacity) {
            return;
        }

        List<LectureEnrollmentEntity> waiting = sortByRequestedOrder(enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
                .toList());
        if (waiting.isEmpty()) {
            return;
        }

        Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade();
        boolean useGradeCapacity = capacityByGrade != null && !capacityByGrade.isEmpty()
                && !isAfterApplicationDeadline(lecture, now);

        if (!useGradeCapacity) {
            waiting.get(0).promoteToEnrolled();
            return;
        }

        for (LectureEnrollmentEntity candidate : waiting) {
            Integer grade = extractGradeFromStudentNumber(candidate.getUser().getStudentNumber());
            Integer gradeCapacity = grade == null ? null : capacityByGrade.get(grade);
            if (gradeCapacity == null) continue;

            long taken = enrolled.stream()
                    .filter(e -> grade.equals(extractGradeFromStudentNumber(e.getUser().getStudentNumber())))
                    .count();
            if (taken < gradeCapacity) {
                candidate.promoteToEnrolled();
                return;
            }
        }
    }

    public void refreshLectureLifecycle(LectureEntity lecture, LocalDateTime now) {
        if (lecture.getId() == null) {
            LocalDateTime lectureEndDateTime = lecture.getLectureEndDateTime();
            if (lecture.getStatus() != LectureStatus.CLOSE && lectureEndDateTime != null && now.isAfter(lectureEndDateTime)) {
                lecture.close();
            }
            return;
        }

        long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.ENROLLED);
        refreshLectureLifecycle(lecture, now, enrolledCount);
    }

    public void refreshLectureLifecycle(LectureEntity lecture, LocalDateTime now, long enrolledCount) {
        lecture.setStatus(resolveStatus(lecture, now, enrolledCount));
    }

    /**
     * 엔티티를 바꾸지 않고 현재 시점의 강연 상태만 계산한다.
     * 읽기 전용 트랜잭션에서는 이 값으로 응답을 만들고, 실제 반영은 쓰기 트랜잭션과 스케줄러가 맡는다.
     */
    public LectureStatus resolveStatus(LectureEntity lecture, LocalDateTime now, long enrolledCount) {
        if (lecture.getStatus() == LectureStatus.CLOSE) return LectureStatus.CLOSE;

        // 1. 강연 종료 시간이 지났으면 CLOSE
        LocalDateTime lectureEndDateTime = lecture.getLectureEndDateTime();
        if (lectureEndDateTime != null && now.isAfter(lectureEndDateTime)) {
            return LectureStatus.CLOSE;
        }

        // 2. 승인되지 않은 강연은 상태 전환을 하지 않음
        if (lecture.getApprovalStatus() != ApprovalStatus.APPROVED) {
            return lecture.getStatus();
        }

        // 3. 신청 마감 기한이 지난 경우
        if (lecture.getApplicationDeadline() != null && now.isAfter(lecture.getApplicationDeadline())) {
            return enrolledCount >= CONFIRM_THRESHOLD ? LectureStatus.CONFIRMED : LectureStatus.UNCONFIRMED;
        }

        // 4. 승인 상태이고 마감 기한 전인 경우: 10명 이상이면 확정(CONFIRMED), 아니면 모집 중(OPEN)
        return enrolledCount >= CONFIRM_THRESHOLD ? LectureStatus.CONFIRMED : LectureStatus.OPEN;
    }

    public int resolveTotalCapacity(LectureEntity lecture) {
        if (lecture.getTotalCapacity() != null) return lecture.getTotalCapacity();
        Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade();
        if (capacityByGrade == null || capacityByGrade.isEmpty()) return 0;

        return capacityByGrade.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public Integer extractGradeFromStudentNumber(String studentNumber) {
        if (studentNumber == null || studentNumber.isEmpty()) return null;
        try {
            return Integer.parseInt(studentNumber.substring(0, 1));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAfterApplicationDeadline(LectureEntity lecture, LocalDateTime now) {
        return lecture.getApplicationDeadline() != null && now.isAfter(lecture.getApplicationDeadline());
    }

    private List<LectureEnrollmentEntity> sortByRequestedOrder(List<LectureEnrollmentEntity> enrollments) {
        return enrollments.stream()
                .sorted(Comparator.comparing(LectureEnrollmentEntity::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LectureEnrollmentEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}