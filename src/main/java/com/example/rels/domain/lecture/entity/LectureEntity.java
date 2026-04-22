package com.example.rels.domain.lecture.entity;

import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyColumn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

@Entity
@Table(name = "lectures")
public class LectureEntity {
	@ElementCollection
	@CollectionTable(name = "lecture_capacity_by_grade", joinColumns = @JoinColumn(name = "lecture_id"))
	@MapKeyColumn(name = "grade")
	@Column(name = "capacity")
	private Map<Integer, Integer> capacityByGrade = new HashMap<>();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

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

	public LectureEntity(String title, String description, UserEntity creator, String lectureLocation, LocalDate lectureDate, LocalTime lectureTime) {
		this.title = title;
		this.description = description;
		this.creator = creator;
		this.status = LectureStatus.OPEN;
		this.capacityByGrade = new HashMap<>();
		this.lectureLocation = lectureLocation;
		this.lectureDate = lectureDate;
		this.lectureTime = lectureTime;
	}

	public LectureEntity(String title, String description, UserEntity creator) {
		this(title, description, creator, null, null, null);
	}

	public Map<Integer, Integer> getCapacityByGrade() {
		return capacityByGrade;
	}

	public void setCapacityByGrade(Map<Integer, Integer> capacityByGrade) {
		if (capacityByGrade == null) {
			this.capacityByGrade = new HashMap<>();
		} else {
			this.capacityByGrade = capacityByGrade;
		}
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

	public void updateBasicInfo(String title, String description) {
		this.title = title;
		this.description = description;
	}

	public void confirm() {
		this.status = LectureStatus.CONFIRMED;
	}

	   public void updateAllDetails(String title, String description, Map<Integer, Integer> capacityByGrade, String lectureLocation, LocalDate lectureDate, LocalTime lectureTime) {
		   this.title = title;
		   this.description = description;
		   setCapacityByGrade(capacityByGrade);
		   this.lectureLocation = lectureLocation;
		   this.lectureDate = lectureDate;
		   this.lectureTime = lectureTime;
	   }
}
