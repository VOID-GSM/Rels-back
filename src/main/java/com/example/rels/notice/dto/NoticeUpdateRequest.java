package com.example.rels.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 5000) String content) {
}

