package com.example.rels.domain.lecture.dto;

import java.util.List;

public record EnrollmentListResponse(
    List<EnrollmentUserResponse> enrolled,
    List<EnrollmentUserResponse> waiting
) {}

