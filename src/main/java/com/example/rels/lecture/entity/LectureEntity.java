package com.example.rels.lecture.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.rels.auth.domain.user.entity.UserEntity;

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

@Entity
@Table(name = "lectures")
public class LectureEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private int capacity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "creator_id", nullable = false)
	private UserEntity creator;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LectureStatus status;

	@Column(name = "lecture_location", length = 255)
	private String lectureLocation;

	@Column(name = "lecture_date")
	private LocalDate lectureDate;

	@Column(name = "lecture_time")
	private LocalTime lectureTime;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected LectureEntity() {
	}

	public LectureEntity(String title, String description, int capacity, UserEntity creator) {
		this.title = title;
		this.description = description;
		this.capacity = capacity;
		this.creator = creator;
		this.status = LectureStatus.OPEN;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public int getCapacity() {
		return capacity;
	}

	public UserEntity getCreator() {
		return creator;
	}

	public LectureStatus getStatus() {
		return status;
	}

	public String getLectureLocation() {
		return lectureLocation;
	}

	public LocalDate getLectureDate() {
		return lectureDate;
	}

	public LocalTime getLectureTime() {
		return lectureTime;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void updateBasicInfo(String title, String description, int capacity) {
		this.title = title;
		this.description = description;
		this.capacity = capacity;
	}

	public void confirm() {
		this.status = LectureStatus.CONFIRMED;
	}

	public void updateAdminDetails(String lectureLocation, LocalDate lectureDate, LocalTime lectureTime) {
		this.lectureLocation = lectureLocation;
		this.lectureDate = lectureDate;
		this.lectureTime = lectureTime;
	}
}

