package com.example.rels.domain.auth.dto;

import java.util.List;

public record MyLecturesResponse(
        List<MyEnrolledLectureResponse> enrolledLectures,
        List<MyCreatedLectureResponse> createdLectures
) {
}
