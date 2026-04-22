package com.example.rels.lecture.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;


public record LectureUpdateRequest(
		@NotBlank @Size(max = 120) String title,
		@NotBlank @Size(max = 5000) String description,
		Map<Integer, Integer> capacityByGrade
) {
}

