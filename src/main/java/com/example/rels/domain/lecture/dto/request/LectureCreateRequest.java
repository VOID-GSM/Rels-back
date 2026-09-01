package com.example.rels.domain.lecture.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public record LectureCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 800) String description,
		Map<@NotNull @Min(1) @Max(3) Integer, @NotNull @Min(0) Integer> capacityByGrade,
		Integer totalCapacity,
		@NotBlank @Size(max = 255) String lectureLocation,
		@NotNull LocalDate lectureDate,
		@NotNull LocalTime lectureTime
) {
}