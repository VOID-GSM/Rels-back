package com.example.rels.lecture.service;

import com.example.rels.domain.lecture.service.LectureLifecycleHandler;
import com.example.rels.domain.lecture.service.LectureService;
import com.example.rels.domain.lecture.service.LectureTimeValidator;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.lecture.dto.request.LectureCreateRequest;
import com.example.rels.domain.lecture.dto.request.LectureUpdateRequest;
import com.example.rels.domain.lecture.dto.response.LectureDetailResponse;
import com.example.rels.domain.lecture.dto.response.LectureSummaryResponse;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

	@Mock
	private LectureRepository lectureRepository;

	@Mock
	private LectureEnrollmentRepository lectureEnrollmentRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private LectureTimeValidator timeValidator;

	@Mock
	private LectureLifecycleHandler lifecycleHandler;

	private LectureService lectureService;

	@BeforeEach
	void setUp() {
		lectureService = new LectureService(
				lectureRepository,
				lectureEnrollmentRepository,
				userRepository,
				timeValidator,
				lifecycleHandler
		);
	}

	@Test
	void getLecturesUsesBulkEnrollmentCounts() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);

		LectureEntity firstLecture = TestEntityFactory.createLecture("title1", "description1", creator, "장소1", LocalDate.now(), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 11L);
		LectureEntity secondLecture = TestEntityFactory.createLecture("title2", "description2", creator, "장소2", LocalDate.now(), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 12L);

		Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(lectureRepository.findVisibleToUser(eq(ApprovalStatus.APPROVED), eq(2L), any()))
				.thenReturn(new PageImpl<>(List.of(firstLecture, secondLecture), pageable, 2));

		LectureEnrollmentCountProjection projection1_enrolled = mock(LectureEnrollmentCountProjection.class);
		when(projection1_enrolled.getLectureId()).thenReturn(11L);
		when(projection1_enrolled.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(projection1_enrolled.getEnrollmentCount()).thenReturn(3L);

		LectureEnrollmentCountProjection projection1_waiting = mock(LectureEnrollmentCountProjection.class);
		when(projection1_waiting.getLectureId()).thenReturn(11L);
		when(projection1_waiting.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(projection1_waiting.getEnrollmentCount()).thenReturn(1L);

		LectureEnrollmentCountProjection projection2_enrolled = mock(LectureEnrollmentCountProjection.class);
		when(projection2_enrolled.getLectureId()).thenReturn(12L);
		when(projection2_enrolled.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(projection2_enrolled.getEnrollmentCount()).thenReturn(0L);

		LectureEnrollmentCountProjection projection2_waiting = mock(LectureEnrollmentCountProjection.class);
		when(projection2_waiting.getLectureId()).thenReturn(12L);
		when(projection2_waiting.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(projection2_waiting.getEnrollmentCount()).thenReturn(0L);

		when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(11L, 12L)))
				.thenReturn(List.of(projection1_enrolled, projection1_waiting, projection2_enrolled, projection2_waiting));

		doNothing().when(lifecycleHandler).refreshLectureLifecycle(any(LectureEntity.class), any(LocalDateTime.class), anyLong());

		Page<LectureSummaryResponse> lectures = lectureService.getLectures(pageable, 2L);

		assertEquals(2, lectures.getTotalElements());
		assertEquals(2, lectures.getContent().size());
		assertEquals(3L, lectures.getContent().get(0).enrolledCount());
		assertEquals(1L, lectures.getContent().get(0).waitingCount());
		assertEquals(0L, lectures.getContent().get(1).enrolledCount());
		assertEquals(0L, lectures.getContent().get(1).waitingCount());

		verifyNoInteractions(userRepository);
	}

	@Test
	void getLecturesMarksEndedLectureAsClosed() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity endedLecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 11L);

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(lectureRepository.findVisibleToUser(eq(ApprovalStatus.APPROVED), eq(2L), any()))
				.thenReturn(new PageImpl<>(List.of(endedLecture), pageable, 1));

		LectureEnrollmentCountProjection projection_enrolled = mock(LectureEnrollmentCountProjection.class);
		when(projection_enrolled.getLectureId()).thenReturn(11L);
		when(projection_enrolled.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(projection_enrolled.getEnrollmentCount()).thenReturn(0L);

		LectureEnrollmentCountProjection projection_waiting = mock(LectureEnrollmentCountProjection.class);
		when(projection_waiting.getLectureId()).thenReturn(11L);
		when(projection_waiting.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(projection_waiting.getEnrollmentCount()).thenReturn(0L);

		when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(11L)))
				.thenReturn(List.of(projection_enrolled, projection_waiting));

		doNothing().when(lifecycleHandler).refreshLectureLifecycle(any(LectureEntity.class), any(LocalDateTime.class), anyLong());

		Page<LectureSummaryResponse> lectures = lectureService.getLectures(pageable, 2L);

		assertEquals(LectureStatus.CLOSE.name(), lectures.getContent().get(0).lectureStatus());
	}

	@Test
	void getLectureDetailMarksEndedLectureAsClosed() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity endedLecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 11L);

		when(lectureRepository.findById(11L)).thenReturn(Optional.of(endedLecture));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(11L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(11L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(11L, 2L)).thenReturn(Optional.empty());

		doNothing().when(lifecycleHandler).refreshLectureLifecycle(any(LectureEntity.class), any(LocalDateTime.class));

		LectureDetailResponse response = lectureService.getLectureDetail(11L, 2L, Role.USER);

		assertEquals(LectureStatus.CLOSE.name(), response.lectureStatus());
		assertEquals(LectureStatus.CLOSE, endedLecture.getStatus());
	}

	@Test
	void createLectureRejectsTotalAndGradeCapacityTogether() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

		var request = new LectureCreateRequest("title", "description", Map.of(1, 10), 20, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, Set.of());

		var exception = assertThrows(ResponseStatusException.class, () -> lectureService.createLecture(1L, request));
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void createLectureAllowsCapacityAboveThirty() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		var request = new LectureCreateRequest("title", "description", null, 31, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, Set.of());

		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(timeValidator.calculateApplicationDeadline(request.lectureDate())).thenReturn(LocalDateTime.now().plusDays(1));
		when(lectureRepository.save(any(LectureEntity.class))).thenAnswer(invocation -> {
			LectureEntity lecture = invocation.getArgument(0);
			TestEntityFactory.setId(lecture, 1L);
			return lecture;
		});
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

		LectureDetailResponse response = lectureService.createLecture(1L, request);

		assertEquals(31, response.totalCapacity());
	}

	@Test
	void updateLectureAllowsAdminToModifyOtherUserLecture() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

		LectureUpdateRequest request = new LectureUpdateRequest("updated title", "updated description", null, 20, "updated 장소", LocalDate.now().plusDays(2), LocalTime.NOON, Set.of());

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

		LectureDetailResponse response = lectureService.updateLecture(1L, 2L, Role.ADMIN, request);

		assertEquals("updated title", response.title());
		assertEquals("updated description", response.description());
	}

	@Test
	void updateLectureRejectsUserFromModifyingOtherUserLecture() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

		LectureUpdateRequest request = new LectureUpdateRequest("updated title", "updated description", null, 20, "updated 장소", LocalDate.now().plusDays(2), LocalTime.NOON, Set.of());

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(ResponseStatusException.class, () -> lectureService.updateLecture(1L, 2L, Role.USER, request));
		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}

	@Test
	void deleteLectureAllowsAdminToDeleteOtherUserLecture() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		lectureService.deleteLecture(1L, 2L, Role.ADMIN);

		verify(lectureEnrollmentRepository).deleteByLectureId(1L);
		verify(lectureRepository).delete(lecture);
	}

	@Test
	void deleteLectureRejectsUserFromDeletingOtherUserLecture() {
		UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
		LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(ResponseStatusException.class, () -> lectureService.deleteLecture(1L, 2L, Role.USER));
		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}
}
