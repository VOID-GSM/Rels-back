package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.auth.domain.user.entity.Role;
import com.example.rels.auth.domain.user.entity.UserEntity;
import com.example.rels.auth.domain.user.repository.UserRepository;
import com.example.rels.lecture.dto.LectureCreateRequest;
import com.example.rels.lecture.dto.EnrollmentResponse;
import com.example.rels.lecture.dto.LectureEnrollmentListResponse;
import com.example.rels.lecture.dto.LectureDetailResponse;
import com.example.rels.lecture.dto.LectureSummaryResponse;
import com.example.rels.lecture.entity.EnrollmentStatus;
import com.example.rels.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.lecture.entity.LectureEntity;
import com.example.rels.lecture.entity.LectureStatus;
import com.example.rels.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.lecture.repository.LectureRepository;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

	@Mock
	private LectureRepository lectureRepository;

	@Mock
	private LectureEnrollmentRepository lectureEnrollmentRepository;

	@Mock
	private UserRepository userRepository;

	private LectureService lectureService;

	@BeforeEach
	void setUp() {
		lectureService = new LectureService(lectureRepository, lectureEnrollmentRepository, userRepository);
	}

	@Test
	void getLecturesUsesBulkEnrollmentCounts() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity firstLecture = new LectureEntity("title1", "description1", 15, creator);
		LectureEntity secondLecture = new LectureEntity("title2", "description2", 20, creator);
		setId(firstLecture, 11L);
		setId(secondLecture, 12L);

		LectureEnrollmentCountProjection enrolledCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(enrolledCount.getLectureId()).thenReturn(11L);
		when(enrolledCount.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(enrolledCount.getEnrollmentCount()).thenReturn(3L);

		LectureEnrollmentCountProjection waitingCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(waitingCount.getLectureId()).thenReturn(11L);
		when(waitingCount.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(waitingCount.getEnrollmentCount()).thenReturn(1L);

		Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(lectureRepository.findAllByOrderByCreatedAtDesc(pageable))
				.thenReturn(new PageImpl<>(List.of(firstLecture, secondLecture), pageable, 2));
		when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(11L, 12L))).thenReturn(List.of(enrolledCount, waitingCount));

		Page<LectureSummaryResponse> lectures = lectureService.getLectures(pageable);

		assertEquals(2, lectures.getTotalElements());
		assertEquals(2, lectures.getContent().size());
		assertEquals(3L, lectures.getContent().get(0).enrolledCount());
		assertEquals(1L, lectures.getContent().get(0).waitingCount());
		assertEquals(0L, lectures.getContent().get(1).enrolledCount());
		assertEquals(0L, lectures.getContent().get(1).waitingCount());

		verify(lectureEnrollmentRepository).countEnrollmentsByLectureIds(List.of(11L, 12L));
		verifyNoInteractions(userRepository);
	}

	@Test
	void enrollConfirmsLectureAtThreshold() {
		LectureEntity lecture = new LectureEntity("title", "description", 30, new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
		UserEntity applicant = new UserEntity("user@test.com", "user", "1000000001", Role.USER);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(9L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

		EnrollmentResponse response = lectureService.enroll(1L, 2L);

		ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
		verify(lectureEnrollmentRepository).save(captor.capture());
		LectureEnrollmentEntity saved = captor.getValue();

		assertSame(lecture, saved.getLecture());
		assertSame(applicant, saved.getUser());
		assertEquals(EnrollmentStatus.ENROLLED, saved.getStatus());
		assertEquals(LectureStatus.CONFIRMED, lecture.getStatus());
		assertEquals("ENROLLED", response.enrollmentStatus());
		assertEquals(10L, response.enrolledCount());
		assertEquals(0L, response.waitingCount());
	}

	@Test
	void enrollMovesToWaitingAfterCapacity() {
		LectureEntity lecture = new LectureEntity("title", "description", 30, new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
		UserEntity applicant = new UserEntity("user@test.com", "user", "1000000001", Role.USER);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(30L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(4L);

		EnrollmentResponse response = lectureService.enroll(1L, 2L);

		ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
		verify(lectureEnrollmentRepository).save(captor.capture());
		LectureEnrollmentEntity saved = captor.getValue();

		assertEquals(EnrollmentStatus.WAITING, saved.getStatus());
		assertEquals(LectureStatus.OPEN, lecture.getStatus());
		assertEquals("WAITING", response.enrollmentStatus());
		assertEquals(30L, response.enrolledCount());
		assertEquals(5L, response.waitingCount());
	}

	@Test
	void cancelPromotesFirstWaitingUser() {
		LectureEntity lecture = new LectureEntity("title", "description", 30, new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
		setId(lecture, 1L);
		UserEntity applicant = new UserEntity("user@test.com", "user", "1000000001", Role.USER);
		UserEntity waitingUser = new UserEntity("wait@test.com", "wait", "1000000002", Role.USER);

		LectureEnrollmentEntity enrolled = new LectureEnrollmentEntity(lecture, applicant, EnrollmentStatus.ENROLLED);
		LectureEnrollmentEntity waiting = new LectureEnrollmentEntity(lecture, waitingUser, EnrollmentStatus.WAITING);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(enrolled));
		when(lectureEnrollmentRepository.findFirstByLectureIdAndStatusOrderByRequestedAtAscIdAsc(1L, EnrollmentStatus.WAITING))
				.thenReturn(Optional.of(waiting));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(30L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(2L);

		EnrollmentResponse response = lectureService.cancelEnrollment(1L, 2L);

		verify(lectureEnrollmentRepository).delete(enrolled);
		assertEquals(EnrollmentStatus.ENROLLED, waiting.getStatus());
		assertEquals("CANCELED", response.enrollmentStatus());
		assertEquals(30L, response.enrolledCount());
		assertEquals(2L, response.waitingCount());
		assertNotNull(response.lectureId());
	}

	@Test
	void getEnrollmentListsSeparatesEnrolledAndWaiting() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator, 10L);
		LectureEntity lecture = new LectureEntity("title", "description", 30, creator);
		setId(lecture, 1L);

		UserEntity enrolledUser = new UserEntity("enrolled@test.com", "enrolled", "1000000001", Role.USER);
		setId(enrolledUser, 21L);
		UserEntity waitingUser = new UserEntity("waiting@test.com", "waiting", "1000000002", Role.USER);
		setId(waitingUser, 22L);

		LectureEnrollmentEntity enrolled = new LectureEnrollmentEntity(lecture, enrolledUser, EnrollmentStatus.ENROLLED);
		LectureEnrollmentEntity waiting = new LectureEnrollmentEntity(lecture, waitingUser, EnrollmentStatus.WAITING);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.findByLectureIdOrderByRequestedAtAscIdAsc(1L))
				.thenReturn(List.of(enrolled, waiting));

		LectureEnrollmentListResponse response = lectureService.getEnrollmentLists(1L, 10L, Role.USER);

		assertEquals(1L, response.lectureId());
		assertEquals(1, response.enrolledApplicants().size());
		assertEquals(21L, response.enrolledApplicants().getFirst().userId());
		assertEquals("enrolled", response.enrolledApplicants().getFirst().name());
		assertEquals(1, response.waitingApplicants().size());
		assertEquals(22L, response.waitingApplicants().getFirst().userId());
		assertEquals("waiting", response.waitingApplicants().getFirst().name());
	}

	@Test
	void getEnrollmentListsForbiddenWhenNotCreatorOrAdmin() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator, 10L);
		LectureEntity lecture = new LectureEntity("title", "description", 30, creator);
		setId(lecture, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> lectureService.getEnrollmentLists(1L, 99L, Role.USER));

		assertEquals(403, exception.getStatusCode().value());
	}

	@Test
	void createLectureUsesRequestedCapacity() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator, 1L);
		LectureCreateRequest request = new LectureCreateRequest("new title", "new description", 42);

		when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
		when(lectureRepository.save(org.mockito.Mockito.any(LectureEntity.class))).thenAnswer(invocation -> {
			LectureEntity saved = invocation.getArgument(0);
			setId(saved, 1L);
			return saved;
		});
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

		LectureDetailResponse response = lectureService.createLecture(1L, request);

		assertEquals(42, response.capacity());
		assertEquals("new title", response.title());
	}

	private void setId(LectureEntity lecture, Long id) {
		try {
			Field field = LectureEntity.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(lecture, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("id 설정 실패", e);
		}
	}

	private void setId(UserEntity user) {
		setId(user, 1L);
	}

	private void setId(UserEntity user, Long id) {
		try {
			Field field = UserEntity.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("id 설정 실패", e);
		}
	}
}


