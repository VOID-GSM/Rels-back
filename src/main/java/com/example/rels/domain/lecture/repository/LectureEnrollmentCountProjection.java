package com.example.rels.domain.lecture.repository;

import com.example.rels.domain.lecture.entity.EnrollmentStatus;

public interface LectureEnrollmentCountProjection {

	Long getLectureId();

	EnrollmentStatus getStatus();

	Long getEnrollmentCount();
}

