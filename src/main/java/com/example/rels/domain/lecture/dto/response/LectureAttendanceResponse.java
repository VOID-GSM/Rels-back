package com.example.rels.domain.lecture.dto.response;

import com.example.rels.domain.lecture.entity.AttendanceStatus;
import java.time.LocalDateTime;

public record LectureAttendanceResponse(
        Long userId,
        String name,
        String studentNumber,
        AttendanceStatus attendanceStatus,
        LocalDateTime attendedAt
) {}