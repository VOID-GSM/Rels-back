package com.example.rels.lecture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.example.rels.domain.lecture.service.LectureService;
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

import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.domain.lecture.dto.EnrollmentResponse;
import com.example.rels.domain.lecture.dto.LectureSummaryResponse;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;

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

		LectureEntity firstLecture = new LectureEntity("title1", "description1", creator);
		LectureEntity secondLecture = new LectureEntity("title2", "description2", creator);
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
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
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
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
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
		LectureEntity lecture = new LectureEntity("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER));
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
		try {
			Field field = UserEntity.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, 1L);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("id 설정 실패", e);
		}
	}
}


