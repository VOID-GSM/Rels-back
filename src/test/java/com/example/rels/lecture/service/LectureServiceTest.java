package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.rels.domain.lecture.service.LectureLifecycleHandler;
import com.example.rels.domain.lecture.service.LectureTimeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.example.rels.domain.lecture.dto.request.LectureCreateRequest;
import com.example.rels.domain.lecture.dto.request.LectureUpdateRequest;
import com.example.rels.domain.lecture.dto.response.EnrollmentResponse;
import com.example.rels.domain.lecture.dto.response.LectureDetailResponse;
import com.example.rels.domain.lecture.dto.response.LectureSummaryResponse;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.lecture.service.LectureService;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;

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
		LectureTimeValidator timeValidatorMock = org.mockito.Mockito.mock(LectureTimeValidator.class);
		LectureLifecycleHandler lifecycleHandlerMock = org.mockito.Mockito.mock(LectureLifecycleHandler.class);

		lectureService = new LectureService(
				lectureRepository,
				lectureEnrollmentRepository,
				userRepository,
				timeValidatorMock,
				lifecycleHandlerMock
		);

		LectureEnrollmentEntity savedMock = org.mockito.Mockito.mock(LectureEnrollmentEntity.class);
		org.mockito.Mockito.lenient().when(savedMock.getRequestedAt()).thenReturn(LocalDateTime.now());
		org.mockito.Mockito.lenient().when(lectureEnrollmentRepository.save(org.mockito.ArgumentMatchers.any(LectureEnrollmentEntity.class)))
				.thenReturn(savedMock);
	}

	@Test
	void getLecturesUsesBulkEnrollmentCounts() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity firstLecture = new LectureEntity("title1", "description1", creator, "장소1", java.time.LocalDate.now(), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		LectureEntity secondLecture = new LectureEntity("title2", "description2", creator, "장소2", java.time.LocalDate.now(), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		setId(firstLecture, 11L);
		setId(secondLecture, 12L);
		setCreatedAt(firstLecture, LocalDateTime.now());
		setCreatedAt(secondLecture, LocalDateTime.now());
		setApprovalStatus(firstLecture, ApprovalStatus.APPROVED);
		setApprovalStatus(secondLecture, ApprovalStatus.APPROVED);

		LectureEnrollmentCountProjection enrolledCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(enrolledCount.getLectureId()).thenReturn(11L);
		when(enrolledCount.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(enrolledCount.getEnrollmentCount()).thenReturn(3L);

		LectureEnrollmentCountProjection waitingCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(waitingCount.getLectureId()).thenReturn(11L);
		when(waitingCount.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(waitingCount.getEnrollmentCount()).thenReturn(1L);

		Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(lectureRepository.findAllByApprovalStatusOrCreatorIdOrderByCreatedAtDesc(eq(ApprovalStatus.APPROVED), eq(2L), any()))
				.thenReturn(new PageImpl<>(List.of(firstLecture, secondLecture), pageable, 2));
		when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(11L, 12L))).thenReturn(List.of(enrolledCount, waitingCount));

		Page<LectureSummaryResponse> lectures = lectureService.getLectures(pageable, 2L);

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
	void getLecturesMarksEndedLectureAsClosed() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity endedLecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		setId(endedLecture, 11L);
		setCreatedAt(endedLecture, LocalDateTime.now().minusDays(2));
		setApprovalStatus(endedLecture, ApprovalStatus.APPROVED);

		LectureEnrollmentCountProjection enrolledCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(enrolledCount.getLectureId()).thenReturn(11L);
		when(enrolledCount.getStatus()).thenReturn(EnrollmentStatus.ENROLLED);
		when(enrolledCount.getEnrollmentCount()).thenReturn(0L);

		LectureEnrollmentCountProjection waitingCount = org.mockito.Mockito.mock(LectureEnrollmentCountProjection.class);
		when(waitingCount.getLectureId()).thenReturn(11L);
		when(waitingCount.getStatus()).thenReturn(EnrollmentStatus.WAITING);
		when(waitingCount.getEnrollmentCount()).thenReturn(0L);

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
		when(lectureRepository.findAllByApprovalStatusOrCreatorIdOrderByCreatedAtDesc(eq(ApprovalStatus.APPROVED), eq(2L), any()))
				.thenReturn(new PageImpl<>(List.of(endedLecture), pageable, 1));
		when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(11L))).thenReturn(List.of(enrolledCount, waitingCount));

		Page<LectureSummaryResponse> lectures = lectureService.getLectures(pageable, 2L);

		assertEquals(LectureStatus.CLOSE.name(), lectures.getContent().getFirst().lectureStatus());
	}

	@Test
	void getLectureDetailMarksEndedLectureAsClosed() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity endedLecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		setId(endedLecture, 11L);
		setCreatedAt(endedLecture, LocalDateTime.now().minusDays(2));
		setApprovalStatus(endedLecture, ApprovalStatus.APPROVED);

		when(lectureRepository.findById(11L)).thenReturn(Optional.of(endedLecture));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(11L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(11L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(11L, 2L)).thenReturn(Optional.empty());

		LectureDetailResponse response = lectureService.getLectureDetail(11L, 2L, Role.USER);

		assertEquals(LectureStatus.CLOSE.name(), response.lectureStatus());
		assertEquals(LectureStatus.CLOSE, endedLecture.getStatus());
	}

	@Test
	void enrollRejectsEndedLecture() {
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		setId(lecture, 1L);
		setCreatedAt(lecture, LocalDateTime.now().minusDays(2));
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> lectureService.enroll(1L, 2L));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		assertEquals(LectureStatus.CLOSE, lecture.getStatus());
	}

	@Test
	@DisplayName("수강 신청 오픈 시간 전 신청 시 예외가 발생한다")
	void enrollRejectsBeforeOpenTime() {
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().plusDays(2), 30);
		setId(lecture, 1L);

		// 생성 시각을 오늘 17:00로 설정하여 오픈 시각(내일 16:20) 이전 신청 상황을 연출
		setCreatedAt(lecture, LocalDateTime.now().toLocalDate().atTime(17, 0));
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> lectureService.enroll(1L, 2L));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		assertTrue(exception.getReason().contains("오후 4시 20분부터 가능합니다."));
	}

	@Test
	void enrollConfirmsLectureAtThreshold() {
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", java.time.LocalDate.now().plusDays(1), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), 30);
		setCreatedAt(lecture, LocalDateTime.now().minusDays(2)); // 이미 신청 시간이 오픈된 강연
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);
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
	void enrollConfirmsLectureAboveThreshold() {
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", java.time.LocalDate.now().plusDays(1), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), 30);
		setCreatedAt(lecture, LocalDateTime.now().minusDays(2)); // 이미 신청 시간이 오픈된 강연
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);
		UserEntity applicant = new UserEntity("user@test.com", "user", "1000000001", Role.USER);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(10L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

		EnrollmentResponse response = lectureService.enroll(1L, 2L);

		ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
		verify(lectureEnrollmentRepository).save(captor.capture());

		assertEquals(LectureStatus.CONFIRMED, lecture.getStatus());
		assertEquals("ENROLLED", response.enrollmentStatus());
		assertEquals(11L, response.enrolledCount());
	}

	@Test
	void createLectureRejectsTotalAndGradeCapacityTogether() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);
		org.mockito.Mockito.lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

		var request = new LectureCreateRequest(
				"title",
				"description",
				Map.of(1, 10),
				20,
				"장소",
				LocalDate.now().plusDays(1),
				LocalTime.NOON);

		var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> lectureService.createLecture(1L, request));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void enrollMovesToWaitingAfterCapacity() {
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", java.time.LocalDate.now().plusDays(1), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), 30);
		setCreatedAt(lecture, LocalDateTime.now().minusDays(2)); // 이미 신청 시간이 오픈된 강연
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);
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
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", java.time.LocalDate.now(), java.time.LocalTime.NOON, LocalDateTime.now().plusDays(1), null);
		setId(lecture, 1L);
		setCreatedAt(lecture, LocalDateTime.now().minusDays(1));
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);
		UserEntity applicant = new UserEntity("user@test.com", "user", "1000000001", Role.USER);
		UserEntity waitingUser = new UserEntity("wait@test.com", "wait", "1000000002", Role.USER);

		LectureEnrollmentEntity enrolled = new LectureEnrollmentEntity(lecture, applicant, EnrollmentStatus.ENROLLED);
		LectureEnrollmentEntity waiting = new LectureEnrollmentEntity(lecture, waitingUser, EnrollmentStatus.WAITING);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(enrolled));
		when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(waiting));
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
	void enrollReadsGradeFromFirstDigitOfStudentNumber() {
		// 학번 "3204"는 3학년 2반이다. 두 번째 자리를 읽으면 2학년으로 잘못 판정된다.
		LectureEntity lecture = gradeCapacityLecture(Map.of(1, 1, 2, 1, 3, 1), LocalDateTime.now().plusDays(1));
		UserEntity secondGrade = new UserEntity("second@test.com", "second", "2204", Role.USER);
		UserEntity applicant = new UserEntity("third@test.com", "third", "3204", Role.USER);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(1L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findAllByLectureId(1L))
				.thenReturn(List.of(enrollment(lecture, secondGrade, EnrollmentStatus.ENROLLED, 1L)));

		EnrollmentResponse response = lectureService.enroll(1L, 2L);

		assertEquals("ENROLLED", response.enrollmentStatus());
	}

	@Test
	void enrollMovesToWaitingWhenGradeHasNoSeat() {
		LectureEntity lecture = gradeCapacityLecture(Map.of(1, 5, 2, 5), LocalDateTime.now().plusDays(1));
		UserEntity applicant = new UserEntity("third@test.com", "third", "3204", Role.USER);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

		EnrollmentResponse response = lectureService.enroll(1L, 2L);

		assertEquals("WAITING", response.enrollmentStatus());
	}

	@Test
	void cancelPromotesWaitingUserOfTheGradeThatFreedUpSeat() {
		LectureEntity lecture = gradeCapacityLecture(Map.of(1, 1, 2, 1), LocalDateTime.now().plusDays(1));
		UserEntity firstGrade = new UserEntity("first@test.com", "first", "1101", Role.USER);
		UserEntity secondGrade = new UserEntity("second@test.com", "second", "2101", Role.USER);

		LectureEnrollmentEntity canceled = enrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
		LectureEnrollmentEntity stillEnrolled = enrollment(lecture, secondGrade, EnrollmentStatus.ENROLLED, 2L);
		// 2학년이 먼저 대기를 걸었지만 2학년 자리는 그대로 차 있다.
		LectureEnrollmentEntity waitingSecondGrade = enrollment(lecture,
				new UserEntity("second2@test.com", "second2", "2102", Role.USER), EnrollmentStatus.WAITING, 3L);
		LectureEnrollmentEntity waitingFirstGrade = enrollment(lecture,
				new UserEntity("first2@test.com", "first2", "1102", Role.USER), EnrollmentStatus.WAITING, 4L);

		when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(canceled));
		when(lectureEnrollmentRepository.findAllByLectureId(1L))
				.thenReturn(List.of(stillEnrolled, waitingSecondGrade, waitingFirstGrade));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(2L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

		lectureService.cancelEnrollment(1L, 2L);

		assertEquals(EnrollmentStatus.WAITING, waitingSecondGrade.getStatus());
		assertEquals(EnrollmentStatus.ENROLLED, waitingFirstGrade.getStatus());
	}

	@Test
	void syncPromotesWaitingUsersUpToTotalCapacityAfterDeadline() {
		// 학년 정원은 신청받는 동안만 적용한다. 마감 뒤에는 남은 자리를 순번대로 채운다.
		LectureEntity lecture = gradeCapacityLecture(Map.of(1, 1, 2, 1, 3, 1), LocalDateTime.now().minusHours(1));
		UserEntity firstGrade = new UserEntity("first@test.com", "first", "1101", Role.USER);

		LectureEnrollmentEntity enrolled = enrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
		LectureEnrollmentEntity firstWaiting = enrollment(lecture,
				new UserEntity("second@test.com", "second", "2101", Role.USER), EnrollmentStatus.WAITING, 2L);
		LectureEnrollmentEntity secondWaiting = enrollment(lecture,
				new UserEntity("second2@test.com", "second2", "2102", Role.USER), EnrollmentStatus.WAITING, 3L);
		LectureEnrollmentEntity thirdWaiting = enrollment(lecture,
				new UserEntity("second3@test.com", "second3", "2103", Role.USER), EnrollmentStatus.WAITING, 4L);

		when(lectureRepository.findAll()).thenReturn(List.of(lecture));
		when(lectureEnrollmentRepository.findAllByLectureId(1L))
				.thenReturn(List.of(enrolled, firstWaiting, secondWaiting, thirdWaiting));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(3L);

		lectureService.syncLectureStatuses();

		assertEquals(EnrollmentStatus.ENROLLED, firstWaiting.getStatus());
		assertEquals(EnrollmentStatus.ENROLLED, secondWaiting.getStatus());
		// 정원 3명을 채웠으므로 네 번째는 그대로 대기다.
		assertEquals(EnrollmentStatus.WAITING, thirdWaiting.getStatus());
	}

	/** 학년별 정원으로 만든 강연. 강연 자체는 아직 끝나지 않은 시각으로 둔다. */
	private LectureEntity gradeCapacityLecture(Map<Integer, Integer> capacityByGrade, LocalDateTime applicationDeadline) {
		LectureEntity lecture = new LectureEntity("title", "description",
				new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소",
				LocalDate.now().plusDays(7), LocalTime.NOON, applicationDeadline, null);
		setId(lecture, 1L);
		setApprovalStatus(lecture, ApprovalStatus.APPROVED);
		// 신청은 개설 당일 16:20부터 열린다. 이미 열린 강연으로 둔다.
		setCreatedAt(lecture, LocalDateTime.now().minusDays(2).toLocalDate().atTime(9, 0));
		lecture.setCapacityByGrade(capacityByGrade);
		return lecture;
	}

	private LectureEnrollmentEntity enrollment(LectureEntity lecture, UserEntity user, EnrollmentStatus status, Long id) {
		LectureEnrollmentEntity enrollment = new LectureEnrollmentEntity(lecture, user, status);
		try {
			Field idField = LectureEnrollmentEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(enrollment, id);
			Field requestedAtField = LectureEnrollmentEntity.class.getDeclaredField("requestedAt");
			requestedAtField.setAccessible(true);
			requestedAtField.set(enrollment, LocalDateTime.now().minusDays(1).plusMinutes(id));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return enrollment;
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

	private void setCreatedAt(LectureEntity lecture, LocalDateTime createdAt) {
		try {
			Field field = LectureEntity.class.getSuperclass().getDeclaredField("createdAt");
			field.setAccessible(true);
			field.set(lecture, createdAt);
		} catch (NoSuchFieldException e) {
			try {
				Field field = LectureEntity.class.getDeclaredField("createdAt");
				field.setAccessible(true);
				field.set(lecture, createdAt);
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("createdAt 설정 실패", ex);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("createdAt 설정 실패", e);
		}
	}

	private void setApprovalStatus(LectureEntity lecture, ApprovalStatus status) {
		try {
			Field field = LectureEntity.class.getDeclaredField("approvalStatus");
			field.setAccessible(true);
			field.set(lecture, status);
		} catch (ReflectiveOperationException e) {
			try {
				lecture.updateApprovalStatus(status, null);
			} catch (Exception ex) {
				throw new IllegalStateException("approvalStatus 설정 실패", ex);
			}
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

	@Test
	void updateLectureAllowsAdminToModifyOtherUserLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);
		UserEntity admin = new UserEntity("admin@test.com", "admin", "2000000000", Role.ADMIN);
		setId(admin, 2L);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		LectureUpdateRequest request = new LectureUpdateRequest(
				"updated title",
				"updated description",
				null,
				20,
				"updated 장소",
				LocalDate.now().plusDays(2),
				LocalTime.NOON
		);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

		LectureDetailResponse response = lectureService.updateLecture(1L, 2L, Role.ADMIN, request);

		assertEquals("updated title", response.title());
		assertEquals("updated description", response.description());
	}

	@Test
	void deleteLectureAllowsAdminToDeleteOtherUserLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);
		UserEntity admin = new UserEntity("admin@test.com", "admin", "2000000000", Role.ADMIN);
		setId(admin, 2L);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		lectureService.deleteLecture(1L, 2L, Role.ADMIN);

		verify(lectureEnrollmentRepository).deleteByLectureId(1L);
		verify(lectureRepository).delete(lecture);
	}

	@Test
	void updateLectureRejectsUserFromModifyingOtherUserLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);
		UserEntity otherUser = new UserEntity("other@test.com", "other", "2000000000", Role.USER);
		setId(otherUser, 2L);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		LectureUpdateRequest request = new LectureUpdateRequest(
				"updated title",
				"updated description",
				null,
				20,
				"updated 장소",
				LocalDate.now().plusDays(2),
				LocalTime.NOON
		);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> lectureService.updateLecture(1L, 2L, Role.USER, request));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}

	@Test
	void deleteLectureRejectsUserFromDeletingOtherUserLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);
		UserEntity otherUser = new UserEntity("other@test.com", "other", "2000000000", Role.USER);
		setId(otherUser, 2L);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> lectureService.deleteLecture(1L, 2L, Role.USER));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}

	@Test
	void updateLectureAllowsCreatorToModifyOwnLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
		when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
		when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

		LectureUpdateRequest request = new LectureUpdateRequest(
				"updated title",
				"updated description",
				null,
				20,
				"updated 장소",
				LocalDate.now().plusDays(2),
				LocalTime.NOON
		);

		LectureDetailResponse response = lectureService.updateLecture(1L, 1L, Role.USER, request);

		assertEquals("updated title", response.title());
		assertEquals("updated description", response.description());
	}

	@Test
	void deleteLectureAllowsCreatorToDeleteOwnLecture() {
		UserEntity creator = new UserEntity("creator@test.com", "creator", "1000000000", Role.USER);
		setId(creator);

		LectureEntity lecture = new LectureEntity("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20);
		setId(lecture, 1L);

		when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

		lectureService.deleteLecture(1L, 1L, Role.USER);

		verify(lectureEnrollmentRepository).deleteByLectureId(1L);
		verify(lectureRepository).delete(lecture);
	}
}