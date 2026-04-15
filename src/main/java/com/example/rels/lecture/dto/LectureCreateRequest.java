
package com.example.rels.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record LectureCreateRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 5000) String description,
    @NotNull Map<Integer, Integer> gradeCapacities
) {}

