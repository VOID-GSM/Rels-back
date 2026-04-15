package com.example.rels.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LectureCreateRequest(
		@NotBlank @Size(max = 120) String title,
		@NotBlank @Size(max = 5000) String description,
		@Min(1) int capacity) {
}

