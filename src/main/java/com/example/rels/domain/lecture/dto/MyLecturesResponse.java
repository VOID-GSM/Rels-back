package com.example.rels.domain.auth.dto;

import com.example.rels.domain.lecture.dto.MyCreatedLectureResponse;
import com.example.rels.domain.lecture.dto.MyEnrolledLectureResponse;

import java.util.List;

public record MyLecturesResponse(
        List<MyEnrolledLectureResponse> enrolledLectures,
        List<MyCreatedLectureResponse> createdLectures
) {
}
