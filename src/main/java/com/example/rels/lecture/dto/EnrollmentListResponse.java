package com.example.rels.lecture.dto;

import java.util.List;

public record EnrollmentListResponse(
    List<EnrollmentUserResponse> enrolled,
    List<EnrollmentUserResponse> waiting
) {}

