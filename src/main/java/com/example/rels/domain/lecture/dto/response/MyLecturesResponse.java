package com.example.rels.domain.lecture.dto.response;

import java.util.List;

public record MyLecturesResponse(
        List<MyEnrolledLectureResponse> enrolledLectures,
        List<MyCreatedLectureResponse> createdLectures
) {
}
