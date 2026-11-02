package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.rels.domain.lecture.dto.response.EnrollmentListResponse;
import com.example.rels.domain.lecture.dto.response.LectureSummaryResponse;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.lecture.service.LectureEnrollmentService;
import com.example.rels.domain.lecture.service.LectureService;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = {
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"rels.lecture.lifecycle-sync-delay-ms=3600000"
})
class LectureQueryPerformanceTest {

	private static final int LECTURE_COUNT = 30;
	private static final int ENROLLMENT_COUNT = 20;
	private static final int PAGE_SIZE = 5;

	@Autowired
	private LectureService lectureService;

	@Autowired
	private LectureEnrollmentService lectureEnrollmentService;

	@Autowired
	private LectureRepository lectureRepository;

	@Autowired
	private LectureEnrollmentRepository lectureEnrollmentRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Statistics statistics;
	private UserEntity creator;
	private LectureEntity firstLecture;

	@BeforeEach
	void setUp() {
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

		creator = userRepository.save(new UserEntity("creator@test.com", "creator", "1101", Role.USER));

		List<LectureEntity> lectures = new ArrayList<>();
		for (int i = 0; i < LECTURE_COUNT; i++) {
			LectureEntity lecture = new LectureEntity("lecture" + i, "description", creator);
			lecture.setCapacityByGrade(Map.of(1, 10));
			lecture.updateApprovalStatus(ApprovalStatus.APPROVED, null);
			lectures.add(lecture);
		}
		firstLecture = lectureRepository.saveAll(lectures).get(0);

		List<LectureEnrollmentEntity> enrollments = new ArrayList<>();
		for (int i = 0; i < ENROLLMENT_COUNT; i++) {
			UserEntity student = userRepository.save(
					new UserEntity("student" + i + "@test.com", "student" + i, "2" + (100 + i), Role.USER));
			enrollments.add(new LectureEnrollmentEntity(firstLecture, student, EnrollmentStatus.ENROLLED));
		}
		lectureEnrollmentRepository.saveAll(enrollments);

		statistics.clear();
	}

	@AfterEach
	void tearDown() {
		lectureEnrollmentRepository.deleteAll();
		lectureRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void lectureListLoadsOnlyRequestedPage() {
		Page<LectureSummaryResponse> page = lectureService.getLectures(pageRequest(), null);

		assertEquals(PAGE_SIZE, page.getContent().size());
		assertEquals(LECTURE_COUNT, page.getTotalElements());
		assertEquals(PAGE_SIZE, statistics.getEntityStatistics(LectureEntity.class.getName()).getLoadCount());
	}

	@Test
	void lectureListQueryCountDoesNotGrowWithPageSize() {
		Page<LectureSummaryResponse> page = lectureService.getLectures(pageRequest(), creator.getId());

		page.getContent().forEach(lecture -> {
			assertEquals(Map.of(1, 10), lecture.capacityByGrade());
			assertEquals(1, lecture.speakers().size());
		});
		assertTrue(statistics.getPrepareStatementCount() <= 6,
				"쿼리 수: " + statistics.getPrepareStatementCount());
	}

	@Test
	void enrollmentListQueryCountDoesNotGrowWithEnrollments() {
		EnrollmentListResponse response = lectureEnrollmentService.getEnrollments(firstLecture.getId(), creator.getId(), Role.USER);

		assertEquals(ENROLLMENT_COUNT, response.enrolled().size());
		assertTrue(statistics.getPrepareStatementCount() <= 2,
				"쿼리 수: " + statistics.getPrepareStatementCount());
	}

	private PageRequest pageRequest() {
		return PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
	}
}
