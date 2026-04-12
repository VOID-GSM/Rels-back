package com.example.rels.lecture.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LectureAdminDetailsRequest(
		@NotBlank @Size(max = 255) String lectureLocation,
		@NotNull LocalDate lectureDate,
		@NotNull LocalTime lectureTime) {
}

