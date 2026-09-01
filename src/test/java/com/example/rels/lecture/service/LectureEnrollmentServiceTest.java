package com.example.rels.lecture.service;

import com.example.rels.domain.lecture.service.LectureLifecycleHandler;
import com.example.rels.domain.lecture.service.LectureService;
import com.example.rels.domain.lecture.service.LectureTimeValidator;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.lecture.dto.response.EnrollmentResponse;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.lecture.entity.TestEntityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LectureEnrollmentServiceTest {

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

        LectureEnrollmentEntity savedMock = mock(LectureEnrollmentEntity.class);
        lenient().when(savedMock.getRequestedAt()).thenReturn(LocalDateTime.now());
        lenient().when(lectureEnrollmentRepository.save(any(LectureEnrollmentEntity.class))).thenReturn(savedMock);
    }

    @Test
    void enrollRejectsEndedLecture() {
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "강의가 종료되었습니다."))
                .when(timeValidator).validateApplicationTime(any(), any(), any());

        var exception = assertThrows(ResponseStatusException.class, () -> lectureService.enroll(1L, 2L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    @DisplayName("수강 신청 오픈 시간 전 신청 시 예외가 발생한다")
    void enrollRejectsBeforeOpenTime() {
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().plusDays(2), 30, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "오후 4시 20분부터 가능합니다."))
                .when(timeValidator).validateApplicationTime(any(), any(), any());

        var exception = assertThrows(ResponseStatusException.class, () -> lectureService.enroll(1L, 2L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains("오후 4시 20분부터 가능합니다."));
    }

    @Test
    void enrollConfirmsLectureAtThreshold() {
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);

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
        assertEquals("ENROLLED", response.enrollmentStatus());
    }

    @Test
    void enrollMovesToWaitingAfterCapacity() {
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);

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
        assertEquals("WAITING", response.enrollmentStatus());
    }

    @Test
    void enrollReadsGradeFromFirstDigitOfStudentNumber() {
        LectureEntity lecture = TestEntityFactory.createGradeCapacityLecture(Map.of(1, 1, 2, 1, 3, 1), LocalDateTime.now().plusDays(1), 3);
        UserEntity secondGrade = TestEntityFactory.createUser("second@test.com", "second", "2204", Role.USER, 1L);
        UserEntity applicant = TestEntityFactory.createUser("third@test.com", "third", "3204", Role.USER, 2L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(1L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);
        when(lectureEnrollmentRepository.findAllByLectureId(1L))
                .thenReturn(List.of(TestEntityFactory.createEnrollment(lecture, secondGrade, EnrollmentStatus.ENROLLED, 1L)));

        EnrollmentResponse response = lectureService.enroll(1L, 2L);

        assertEquals("ENROLLED", response.enrollmentStatus());
    }

    @Test
    void cancelPromotesFirstWaitingUser() {
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", new UserEntity("creator@test.com", "creator", "1000000000", Role.USER), "장소", LocalDate.now(), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        UserEntity waitingUser = TestEntityFactory.createUser("wait@test.com", "wait", "1000000002", Role.USER, 3L);

        LectureEnrollmentEntity enrolled = TestEntityFactory.createEnrollment(lecture, applicant, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(enrolled));
        when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(waiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(29L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        EnrollmentResponse response = lectureService.cancelEnrollment(1L, 2L);

        verify(lectureEnrollmentRepository).delete(enrolled);
        assertEquals(EnrollmentStatus.ENROLLED, waiting.getStatus());
        assertEquals("CANCELED", response.enrollmentStatus());
    }

    @Test
    void cancelPromotesWaitingUserOfTheGradeThatFreedUpSeat() {
        LectureEntity lecture = TestEntityFactory.createGradeCapacityLecture(Map.of(1, 1, 2, 1), LocalDateTime.now().plusDays(1), 2);
        UserEntity firstGrade = TestEntityFactory.createUser("first@test.com", "first", "1101", Role.USER, 1L);
        UserEntity secondGrade = TestEntityFactory.createUser("second@test.com", "second", "2101", Role.USER, 2L);

        LectureEnrollmentEntity canceled = TestEntityFactory.createEnrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity stillEnrolled = TestEntityFactory.createEnrollment(lecture, secondGrade, EnrollmentStatus.ENROLLED, 2L);
        LectureEnrollmentEntity waitingSecondGrade = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second2@test.com", "second2", "2102", Role.USER, 3L), EnrollmentStatus.WAITING, 3L);
        LectureEnrollmentEntity waitingFirstGrade = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("first2@test.com", "first2", "1102", Role.USER, 4L), EnrollmentStatus.WAITING, 4L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(canceled));
        when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(stillEnrolled, waitingSecondGrade, waitingFirstGrade));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(1L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        lectureService.cancelEnrollment(1L, 2L);

        assertEquals(EnrollmentStatus.WAITING, waitingSecondGrade.getStatus());
        assertEquals(EnrollmentStatus.ENROLLED, waitingFirstGrade.getStatus());
    }

    @Test
    void syncPromotesWaitingUsersUpToTotalCapacityAfterDeadline() {
        LectureEntity lecture = TestEntityFactory.createGradeCapacityLecture(Map.of(1, 1, 2, 1, 3, 1), LocalDateTime.now().minusHours(1), 3);
        UserEntity firstGrade = TestEntityFactory.createUser("first@test.com", "first", "1101", Role.USER, 1L);

        LectureEnrollmentEntity enrolled = TestEntityFactory.createEnrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity firstWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second@test.com", "second", "2101", Role.USER, 2L), EnrollmentStatus.WAITING, 2L);
        LectureEnrollmentEntity secondWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second2@test.com", "second2", "2102", Role.USER, 3L), EnrollmentStatus.WAITING, 3L);
        LectureEnrollmentEntity thirdWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second3@test.com", "second3", "2103", Role.USER, 4L), EnrollmentStatus.WAITING, 4L);

        when(lectureRepository.findAll()).thenReturn(List.of(lecture));
        when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(enrolled, firstWaiting, secondWaiting, thirdWaiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(1L);

        lectureService.syncLectureStatuses();

        assertEquals(EnrollmentStatus.ENROLLED, firstWaiting.getStatus());
        assertEquals(EnrollmentStatus.ENROLLED, secondWaiting.getStatus());
        assertEquals(EnrollmentStatus.WAITING, thirdWaiting.getStatus());
    }
}