package com.example.rels.lecture.service;

import com.example.rels.domain.lecture.dto.request.AttendanceUpdateRequest;
import com.example.rels.domain.lecture.entity.AttendanceStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.lecture.service.LectureAccessValidator;
import com.example.rels.domain.lecture.service.LectureAttendanceService;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LectureAttendanceServiceTest {

	@Mock
	private LectureRepository lectureRepository;

	@Mock
	private LectureEnrollmentRepository lectureEnrollmentRepository;

	private LectureAttendanceService lectureAttendanceService;

	@BeforeEach
	void setUp() {
		lectureAttendanceService = new LectureAttendanceService(
				lectureEnrollmentRepository,
				new LectureAccessValidator(lectureRepository)
		);
	}

	@Test
	@DisplayName("출석 상태 일괄 변경 시 N+1 문제 해결을 위해 배치 조회를 정상 수행한다.")
	void updateAttendancesUsesBatchFetching() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		UserEntity student1 = TestEntityFactory.createUser("student1@test.com", "student1", "2000000000", Role.USER, 2L);
		UserEntity student2 = TestEntityFactory.createUser("student2@test.com", "student2", "3000000000", Role.USER, 3L);
		LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

		LectureEnrollmentEntity enrollment1 = new LectureEnrollmentEntity(lecture, student1, EnrollmentStatus.ENROLLED);
		LectureEnrollmentEntity enrollment2 = new LectureEnrollmentEntity(lecture, student2, EnrollmentStatus.ENROLLED);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.findAllByLectureIdAndUserIdIn(eq(1L), eq(List.of(2L, 3L))))
				.thenReturn(List.of(enrollment1, enrollment2));

		List<AttendanceUpdateRequest> requests = List.of(
				new AttendanceUpdateRequest(2L, AttendanceStatus.ATTENDED),
				new AttendanceUpdateRequest(3L, AttendanceStatus.ABSENT)
		);

		assertDoesNotThrow(() -> lectureAttendanceService.updateAttendances(1L, 1L, Role.USER, requests));
		assertEquals(AttendanceStatus.ATTENDED, enrollment1.getAttendanceStatus());
		assertEquals(AttendanceStatus.ABSENT, enrollment2.getAttendanceStatus());
		verify(lectureEnrollmentRepository).findAllByLectureIdAndUserIdIn(eq(1L), eq(List.of(2L, 3L)));
	}
}
