package com.example.rels.lecture.service;

import com.example.rels.domain.lecture.dto.response.EnrollmentListResponse;
import com.example.rels.domain.lecture.dto.response.EnrollmentResponse;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;
import com.example.rels.domain.lecture.service.LectureAccessValidator;
import com.example.rels.domain.lecture.service.LectureEnrollmentService;
import com.example.rels.domain.lecture.service.LectureLifecycleHandler;
import com.example.rels.domain.lecture.service.LectureStatusScheduler;
import com.example.rels.domain.lecture.service.LectureTimeValidator;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
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

    private LectureLifecycleHandler lifecycleHandler;

    private LectureEnrollmentService lectureEnrollmentService;

    private LectureStatusScheduler lectureStatusScheduler;

    @BeforeEach
    void setUp() {
        lifecycleHandler = new LectureLifecycleHandler(lectureEnrollmentRepository);
        LectureAccessValidator accessValidator = new LectureAccessValidator(lectureRepository);
        lectureEnrollmentService = new LectureEnrollmentService(
                lectureEnrollmentRepository,
                userRepository,
                timeValidator,
                lifecycleHandler,
                accessValidator
        );
        lectureStatusScheduler = new LectureStatusScheduler(lectureRepository, lectureEnrollmentRepository, lifecycleHandler);

        LectureEnrollmentEntity savedMock = mock(LectureEnrollmentEntity.class);
        lenient().when(savedMock.getRequestedAt()).thenReturn(LocalDateTime.now());
        lenient().when(lectureEnrollmentRepository.save(any(LectureEnrollmentEntity.class))).thenReturn(savedMock);
    }

    @Test
    void enrollRejectsEndedLecture() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().minusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), null, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "강의가 종료되었습니다."))
                .when(timeValidator).validateApplicationTime(any(), any());

        var exception = assertThrows(ResponseStatusException.class, () -> lectureEnrollmentService.enroll(1L, 2L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void enrollRejectsSpeaker() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity speaker = TestEntityFactory.createUser("speaker@test.com", "speaker", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        lecture.updateSpeakers(java.util.Set.of(speaker));

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(speaker));

        var exception = assertThrows(ResponseStatusException.class, () -> lectureEnrollmentService.enroll(1L, 2L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(lectureEnrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("수강 신청 오픈 시간 전 신청 시 예외가 발생한다")
    void enrollRejectsBeforeOpenTime() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().plusDays(2), 30, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "오후 4시 20분부터 가능합니다."))
                .when(timeValidator).validateApplicationTime(any(), any());

        var exception = assertThrows(ResponseStatusException.class, () -> lectureEnrollmentService.enroll(1L, 2L));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains("오후 4시 20분부터 가능합니다."));
    }

    @Test
    void enrollConfirmsLectureAtThreshold() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(9L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

        EnrollmentResponse response = lectureEnrollmentService.enroll(1L, 2L);

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
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(30L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(4L);

        EnrollmentResponse response = lectureEnrollmentService.enroll(1L, 2L);

        ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
        verify(lectureEnrollmentRepository).save(captor.capture());
        LectureEnrollmentEntity saved = captor.getValue();

        assertEquals(EnrollmentStatus.WAITING, saved.getStatus());
        assertEquals("WAITING", response.enrollmentStatus());
    }

    @Test
    void enrollAfterApplicationDeadlineAddsUserToWaitingList() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().minusMinutes(1), 30, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

        EnrollmentResponse response = lectureEnrollmentService.enroll(1L, 2L);

        ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
        verify(lectureEnrollmentRepository).save(captor.capture());
        assertEquals(EnrollmentStatus.WAITING, captor.getValue().getStatus());
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

        EnrollmentResponse response = lectureEnrollmentService.enroll(1L, 2L);

        assertEquals("ENROLLED", response.enrollmentStatus());
    }

    @Test
    @DisplayName("마감 전에 신청자가 취소하면 대기 1번이 신청자로 올라간다")
    void cancelPromotesFirstWaitingUserBeforeDeadline() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now(), LocalTime.NOON, LocalDateTime.now().plusDays(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        UserEntity waitingUser = TestEntityFactory.createUser("wait@test.com", "wait", "1000000002", Role.USER, 3L);

        LectureEnrollmentEntity enrolled = TestEntityFactory.createEnrollment(lecture, applicant, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(enrolled));
        when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(waiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(29L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        EnrollmentResponse response = lectureEnrollmentService.cancelEnrollment(1L, 2L);

        verify(lectureEnrollmentRepository).delete(enrolled);
        assertEquals(EnrollmentStatus.ENROLLED, waiting.getStatus());
        assertEquals("CANCELED", response.enrollmentStatus());
    }

    @Test
    @DisplayName("마감 전 승급은 빈 자리가 난 학년의 대기자에게 간다")
    void cancelPromotesWaitingUserOfFreedUpGradeBeforeDeadline() {
        LectureEntity lecture = TestEntityFactory.createGradeCapacityLecture(Map.of(1, 1, 2, 1), LocalDateTime.now().plusDays(1), 2);
        UserEntity firstGrade = TestEntityFactory.createUser("first@test.com", "first", "1101", Role.USER, 1L);
        UserEntity secondGrade = TestEntityFactory.createUser("second@test.com", "second", "2101", Role.USER, 2L);

        LectureEnrollmentEntity canceled = TestEntityFactory.createEnrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity stillEnrolled = TestEntityFactory.createEnrollment(lecture, secondGrade, EnrollmentStatus.ENROLLED, 2L);
        LectureEnrollmentEntity waitingSecondGrade = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second2@test.com", "second2", "2102", Role.USER, 3L), EnrollmentStatus.WAITING, 3L);
        LectureEnrollmentEntity waitingFirstGrade = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("first2@test.com", "first2", "1102", Role.USER, 4L), EnrollmentStatus.WAITING, 4L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(canceled));
        when(lectureEnrollmentRepository.findAllByLectureId(1L))
                .thenReturn(List.of(stillEnrolled, waitingSecondGrade, waitingFirstGrade));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(1L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        lectureEnrollmentService.cancelEnrollment(1L, 2L);

        assertEquals(EnrollmentStatus.WAITING, waitingSecondGrade.getStatus());
        assertEquals(EnrollmentStatus.ENROLLED, waitingFirstGrade.getStatus());
    }

    @Test
    void syncDoesNotAutomaticallyPromoteWaitingUsersAfterDeadline() {
        LectureEntity lecture = TestEntityFactory.createGradeCapacityLecture(Map.of(1, 1, 2, 1, 3, 1), LocalDateTime.now().minusHours(1), 3);
        UserEntity firstGrade = TestEntityFactory.createUser("first@test.com", "first", "1101", Role.USER, 1L);

        LectureEnrollmentEntity enrolled = TestEntityFactory.createEnrollment(lecture, firstGrade, EnrollmentStatus.ENROLLED, 1L);
        LectureEnrollmentEntity firstWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second@test.com", "second", "2101", Role.USER, 2L), EnrollmentStatus.WAITING, 2L);
        LectureEnrollmentEntity secondWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second2@test.com", "second2", "2102", Role.USER, 3L), EnrollmentStatus.WAITING, 3L);
        LectureEnrollmentEntity thirdWaiting = TestEntityFactory.createEnrollment(lecture, TestEntityFactory.createUser("second3@test.com", "second3", "2103", Role.USER, 4L), EnrollmentStatus.WAITING, 4L);

        when(lectureRepository.findAllByStatusNot(LectureStatus.CLOSE)).thenReturn(List.of(lecture));
        LectureEnrollmentCountProjection enrolledCount = enrollmentCount(1L, EnrollmentStatus.ENROLLED, 1L);
        when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(1L))).thenReturn(List.of(enrolledCount));

        lectureStatusScheduler.syncLectureStatuses();

        assertEquals(EnrollmentStatus.WAITING, firstWaiting.getStatus());
        assertEquals(EnrollmentStatus.WAITING, secondWaiting.getStatus());
        assertEquals(EnrollmentStatus.WAITING, thirdWaiting.getStatus());
    }

    @Test
    @DisplayName("상태 동기화는 CLOSE가 아닌 강연만 조회하고 신청 인원을 한 번에 집계한다")
    void syncCountsEnrollmentsInSingleQuery() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 100L);
        LectureEntity confirmedLecture = TestEntityFactory.createLecture("title1", "description1", creator, "장소", LocalDate.now().plusDays(7), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);
        LectureEntity openLecture = TestEntityFactory.createLecture("title2", "description2", creator, "장소", LocalDate.now().plusDays(7), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 2L);

        when(lectureRepository.findAllByStatusNot(LectureStatus.CLOSE)).thenReturn(List.of(confirmedLecture, openLecture));
        LectureEnrollmentCountProjection enrolled = enrollmentCount(1L, EnrollmentStatus.ENROLLED, 10L);
        LectureEnrollmentCountProjection waiting = enrollmentCount(1L, EnrollmentStatus.WAITING, 3L);
        when(lectureEnrollmentRepository.countEnrollmentsByLectureIds(List.of(1L, 2L))).thenReturn(List.of(enrolled, waiting));

        lectureStatusScheduler.syncLectureStatuses();

        assertEquals(LectureStatus.CONFIRMED, confirmedLecture.getStatus());
        assertEquals(LectureStatus.OPEN, openLecture.getStatus());
        verify(lectureRepository, never()).findAll();
        verify(lectureEnrollmentRepository, never()).countByLectureIdAndStatus(any(), any());
    }

    private LectureEnrollmentCountProjection enrollmentCount(Long lectureId, EnrollmentStatus status, Long count) {
        LectureEnrollmentCountProjection projection = mock(LectureEnrollmentCountProjection.class);
        lenient().when(projection.getLectureId()).thenReturn(lectureId);
        lenient().when(projection.getStatus()).thenReturn(status);
        lenient().when(projection.getEnrollmentCount()).thenReturn(count);
        return projection;
    }

    @Test
    @DisplayName("학생회는 정원이 차도 대기자를 수락(ENROLLED)으로 변경할 수 있다")
    void adminCanAcceptWaitingUserEvenWhenCapacityIsFull() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity waitingUser = TestEntityFactory.createUser("wait@test.com", "wait", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().minusMinutes(1), 10, 1L);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L);

        when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(waiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(10L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        EnrollmentResponse response = lectureEnrollmentService.decideWaitingEnrollment(1L, 2L, Role.ADMIN);

        assertEquals(EnrollmentStatus.ENROLLED, waiting.getStatus());
        assertEquals("ENROLLED", response.enrollmentStatus());
        assertEquals(11L, response.enrolledCount());
        assertEquals(0L, response.waitingCount());
    }

    @Test
    @DisplayName("학생/개설자 모두 수강 신청 목록(승인/대기)을 조회할 수 있다")
    void getEnrollmentsLetsUsersSeeRoster() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity enrolledUser = TestEntityFactory.createUser("a@test.com", "a", "1000000001", Role.USER, 2L);
        UserEntity waitingUser = TestEntityFactory.createUser("b@test.com", "b", "1000000002", Role.USER, 3L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().plusDays(1), 10, 1L);

        when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findAllByLectureId(1L)).thenReturn(List.of(
                TestEntityFactory.createEnrollment(lecture, enrolledUser, EnrollmentStatus.ENROLLED, 1L),
                TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L)));

        EnrollmentListResponse response = lectureEnrollmentService.getEnrollments(1L, 9L, Role.USER);

        assertEquals(1, response.enrolled().size());
        assertEquals(1, response.waiting().size());
        assertEquals(2L, response.enrolled().get(0).userId());
        assertEquals(3L, response.waiting().get(0).userId());
    }

    @Test
    void cancelRejectsConfirmedEnrollmentAfterDeadline() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().minusHours(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        LectureEnrollmentEntity enrolled = TestEntityFactory.createEnrollment(lecture, applicant, EnrollmentStatus.ENROLLED, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(enrolled));

        var exception = assertThrows(ResponseStatusException.class,
                () -> lectureEnrollmentService.cancelEnrollment(1L, 2L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(lectureEnrollmentRepository, never()).delete(enrolled);
    }

    @Test
    void cancelAllowsWaitingEnrollmentAfterDeadline() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().minusHours(1), 30, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, applicant, EnrollmentStatus.WAITING, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(waiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(30L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(0L);

        EnrollmentResponse response = lectureEnrollmentService.cancelEnrollment(1L, 2L);

        verify(lectureEnrollmentRepository).delete(waiting);
        assertEquals("CANCELED", response.enrollmentStatus());
    }

    @Test
    @DisplayName("신청 마감 뒤에 들어온 신청은 자리가 남아 있어도 대기로 잡힌다")
    void enrollAfterDeadlineBecomesWaiting() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity applicant = TestEntityFactory.createUser("user@test.com", "user", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().minusHours(1), 30, 1L);

        when(lectureRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lecture));
        when(userRepository.findById(2L)).thenReturn(Optional.of(applicant));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        EnrollmentResponse response = lectureEnrollmentService.enroll(1L, 2L);

        assertEquals("WAITING", response.enrollmentStatus());
        ArgumentCaptor<LectureEnrollmentEntity> captor = ArgumentCaptor.forClass(LectureEnrollmentEntity.class);
        verify(lectureEnrollmentRepository).save(captor.capture());
        assertEquals(EnrollmentStatus.WAITING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("대기자 수락은 개설자가 아니라 학생회만 할 수 있다")
    void creatorCannotDecideWaitingUser() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity waitingUser = TestEntityFactory.createUser("wait@test.com", "wait", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().minusMinutes(1), 10, 1L);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L);

        when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));

        var exception = assertThrows(ResponseStatusException.class,
                () -> lectureEnrollmentService.decideWaitingEnrollment(1L, 2L, Role.USER));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(EnrollmentStatus.WAITING, waiting.getStatus());
    }

    @Test
    @DisplayName("마감 뒤 학생회 수락으로 10명을 채우면 강연이 확정으로 바뀐다")
    void adminAcceptanceConfirmsLectureAtThreshold() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity waitingUser = TestEntityFactory.createUser("wait@test.com", "wait", "1000000001", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(2), LocalTime.NOON, LocalDateTime.now().minusHours(1), 30, 1L);
        lecture.setStatus(LectureStatus.UNCONFIRMED);
        LectureEnrollmentEntity waiting = TestEntityFactory.createEnrollment(lecture, waitingUser, EnrollmentStatus.WAITING, 2L);

        when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(waiting));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(9L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(1L);

        lectureEnrollmentService.decideWaitingEnrollment(1L, 2L, Role.ADMIN);

        assertEquals(EnrollmentStatus.ENROLLED, waiting.getStatus());
        assertEquals(LectureStatus.CONFIRMED, lecture.getStatus());
    }

    @Test
    @DisplayName("대기중인 신청자를 수락(ENROLLED)으로 변경할 수 있다.")
    void decideWaitingEnrollmentPromotesToEnrolled() {
        UserEntity creator = TestEntityFactory.createUser("creator@test.com", "creator", "1000000000", Role.USER, 1L);
        UserEntity student = TestEntityFactory.createUser("student@test.com", "student", "2000000000", Role.USER, 2L);
        LectureEntity lecture = TestEntityFactory.createLecture("title", "description", creator, "장소", LocalDate.now().plusDays(1), LocalTime.NOON, LocalDateTime.now().plusDays(1), 20, 1L);

        LectureEnrollmentEntity waitingEnrollment = new LectureEnrollmentEntity(lecture, student, EnrollmentStatus.WAITING);

        when(lectureRepository.findById(1L)).thenReturn(Optional.of(lecture));
        when(lectureEnrollmentRepository.findByLectureIdAndUserId(1L, 2L)).thenReturn(Optional.of(waitingEnrollment));
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(5L);
        when(lectureEnrollmentRepository.countByLectureIdAndStatus(1L, EnrollmentStatus.WAITING)).thenReturn(2L);

        EnrollmentResponse response = lectureEnrollmentService.decideWaitingEnrollment(1L, 2L, Role.ADMIN);

        assertEquals(EnrollmentStatus.ENROLLED.name(), response.enrollmentStatus());
        assertEquals(6L, response.enrolledCount());
        assertEquals(1L, response.waitingCount());
    }
}
