package com.example.rels.domain.notice.dto;

import java.time.LocalDateTime;

public record NoticeSummaryResponse(
        Long id,
        String title,
        String content,
        Long authorId,
        String authorName,
        LocalDateTime createdAt) {
}

