package com.example.rels.domain.lecture.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;


public record LectureUpdateRequest(
	@NotBlank @Size(max = 120) String title,
	@NotBlank @Size(max = 5000) String description,
	Map<Integer, Integer> capacityByGrade,
	@NotBlank @Size(max = 255) String lectureLocation,
	@NotNull LocalDate lectureDate,
	@NotNull LocalTime lectureTime,
	@NotNull LocalDateTime applicationDeadline
) {
}
