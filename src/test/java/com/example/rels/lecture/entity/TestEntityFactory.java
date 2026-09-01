package com.example.rels.lecture.entity;

import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.user.entity.UserEntity;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

public class TestEntityFactory {

    public static UserEntity createUser(String email, String name, String studentNumber, Role role, Long id) {
        UserEntity user = new UserEntity(email, name, studentNumber, role);
        setField(user, "id", id);
        return user;
    }

    public static LectureEntity createLecture(String title, String description, UserEntity creator, String location,
                                              LocalDate lectureDate, LocalTime lectureTime, LocalDateTime deadline,
                                              Integer totalCapacity, Long id) {
        LectureEntity lecture = new LectureEntity(title, description, creator, location, lectureDate, lectureTime, deadline, totalCapacity);
        setField(lecture, "id", id);
        setField(lecture, "createdAt", LocalDateTime.now().minusDays(2));
        setApprovalStatus(lecture, ApprovalStatus.APPROVED);
        return lecture;
    }

    public static LectureEntity createGradeCapacityLecture(Map<Integer, Integer> capacityByGrade, LocalDateTime applicationDeadline, Integer totalCapacity) {
        UserEntity creator = createUser("creator@test.com", "creator", "1000000000", Role.USER, 100L);
        LectureEntity lecture = createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(7), LocalTime.NOON, applicationDeadline, totalCapacity, 1L);
        lecture.setCapacityByGrade(capacityByGrade);
        return lecture;
    }

    public static LectureEnrollmentEntity createEnrollment(LectureEntity lecture, UserEntity user, EnrollmentStatus status, Long id) {
        LectureEnrollmentEntity enrollment = new LectureEnrollmentEntity(lecture, user, status);
        setField(enrollment, "id", id);
        setField(enrollment, "requestedAt", LocalDateTime.now().minusDays(1).plusMinutes(id != null ? id : 0));
        return enrollment;
    }

    private static void setField(Object target, String fieldName, Object value) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(fieldName + " 필드 설정 오류", e);
            }
        }
    }

    private static void setApprovalStatus(LectureEntity lecture, ApprovalStatus status) {
        try {
            setField(lecture, "approvalStatus", status);
        } catch (Exception e) {
            lecture.updateApprovalStatus(status, null);
        }
    }
}