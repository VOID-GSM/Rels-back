package com.example.rels.domain.lecture.dto.request;

import com.example.rels.domain.lecture.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceUpdateRequest(
        @NotNull Long userId,
        @NotNull AttendanceStatus attendanceStatus
) {}