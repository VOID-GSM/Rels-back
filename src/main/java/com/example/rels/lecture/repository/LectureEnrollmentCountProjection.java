package com.example.rels.lecture.repository;

import com.example.rels.lecture.entity.EnrollmentStatus;

public interface LectureEnrollmentCountProjection {

	Long getLectureId();

	EnrollmentStatus getStatus();

	Long getEnrollmentCount();
}

