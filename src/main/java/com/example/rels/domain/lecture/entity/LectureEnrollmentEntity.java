package com.example.rels.domain.lecture.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.example.rels.domain.user.entity.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "lecture_enrollments", uniqueConstraints = {
		@UniqueConstraint(name = "uk_lecture_enrollments_lecture_user", columnNames = { "lecture_id", "user_id" })
})
public class LectureEnrollmentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lecture_id", nullable = false)
	private LectureEntity lecture;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EnrollmentStatus status;

	@CreationTimestamp
	@Column(name = "requested_at", nullable = false, updatable = false)
	private LocalDateTime requestedAt;

	protected LectureEnrollmentEntity() {
	}

	public LectureEnrollmentEntity(LectureEntity lecture, UserEntity user, EnrollmentStatus status) {
		this.lecture = lecture;
		this.user = user;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public LectureEntity getLecture() {
		return lecture;
	}

	public UserEntity getUser() {
		return user;
	}

	public EnrollmentStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public void promoteToEnrolled() {
		this.status = EnrollmentStatus.ENROLLED;
	}
}

