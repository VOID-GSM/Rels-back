package com.example.rels.domain.lecture.dto.response;

import java.util.List;

public record EnrollmentListResponse(
    List<EnrollmentUserResponse> enrolled,
    List<EnrollmentUserResponse> waiting
) {}

