package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.lecture.dto.response.EnrollmentListResponse;
import com.example.rels.domain.lecture.dto.response.EnrollmentResponse;
import com.example.rels.domain.lecture.dto.response.EnrollmentUserResponse;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;

@Service
public class LectureEnrollmentService {

	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final UserRepository userRepository;
	private final LectureTimeValidator timeValidator;
	private final LectureLifecycleHandler lifecycleHandler;
	private final LectureAccessValidator accessValidator;

	public LectureEnrollmentService(LectureEnrollmentRepository lectureEnrollmentRepository,
									UserRepository userRepository,
									LectureTimeValidator timeValidator,
									LectureLifecycleHandler lifecycleHandler,
									LectureAccessValidator accessValidator) {
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.userRepository = userRepository;
		this.timeValidator = timeValidator;
		this.lifecycleHandler = lifecycleHandler;
		this.accessValidator = accessValidator;
	}

	@Transactional
	public EnrollmentResponse enroll(Long lectureId, Long userId) {
		LectureEntity lecture = accessValidator.requireLectureForUpdate(lectureId);
		if (lecture.getApprovalStatus() != ApprovalStatus.APPROVED) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "승인된 강연만 수강 신청할 수 있습니다.");
		}

		LocalDateTime now = SchoolTime.now();

		LocalDateTime applicationOpenReference = SchoolTime.fromServerTime(
				lecture.getApprovedAt() != null ? lecture.getApprovedAt() : lecture.getCreatedAt());
		timeValidator.validateApplicationTime(applicationOpenReference, now);
		boolean isAfterApplicationDeadline = isAfterApplicationDeadline(lecture, now);

		lifecycleHandler.refreshLectureLifecycle(lecture, now);
		if (lecture.getStatus() == LectureStatus.CLOSE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이미 종료된 강의입니다.");
		}

		UserEntity user = requireUser(userId);
		if (lecture.isSpeaker(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "연사자는 자신의 강연에 수강 신청할 수 없습니다.");
		}

		lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 강의입니다.");
				});

		Integer userGrade = lifecycleHandler.extractGradeFromStudentNumber(user.getStudentNumber());
		Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade() == null ? Map.of() : lecture.getCapacityByGrade();
		Integer totalCapacity = lecture.getTotalCapacity();
		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		boolean useGradeCapacity = !capacityByGrade.isEmpty();
		boolean isFull;
		if (isAfterApplicationDeadline) {
			isFull = true;
		} else if (useGradeCapacity) {
			Integer gradeCapacity = userGrade == null ? null : capacityByGrade.get(userGrade);
			if (gradeCapacity == null) {
				isFull = true;
			} else {
				long gradeEnrolled = countEnrolledInGrade(lectureId, userGrade);
				isFull = gradeEnrolled >= gradeCapacity;
			}
		} else if (totalCapacity != null && totalCapacity > 0) {
			isFull = enrolledCount >= totalCapacity;
		} else {
			isFull = false;
		}

		EnrollmentStatus status = isFull ? EnrollmentStatus.WAITING : EnrollmentStatus.ENROLLED;
		LectureEnrollmentEntity savedEnrollment = lectureEnrollmentRepository.save(new LectureEnrollmentEntity(lecture, user, status));

		if (status == EnrollmentStatus.ENROLLED) {
			lifecycleHandler.refreshLectureLifecycle(lecture, now, enrolledCount + 1);
		}

		long nextEnrolledCount = status == EnrollmentStatus.ENROLLED ? enrolledCount + 1 : enrolledCount;
		long nextWaitingCount = status == EnrollmentStatus.WAITING ? waitingCount + 1 : waitingCount;

		return new EnrollmentResponse(lectureId, status.name(), nextEnrolledCount, nextWaitingCount, savedEnrollment.getRequestedAt());
	}

	private boolean isAfterApplicationDeadline(LectureEntity lecture, LocalDateTime now) {
		return lecture.getApplicationDeadline() != null && now.isAfter(lecture.getApplicationDeadline());
	}

	@Transactional
	public EnrollmentResponse cancelEnrollment(Long lectureId, Long userId) {
		LectureEntity lecture = accessValidator.requireLectureForUpdate(lectureId);
		LocalDateTime now = SchoolTime.now();

		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역이 없습니다."));

		EnrollmentStatus canceledStatus = enrollment.getStatus();

		if (canceledStatus == EnrollmentStatus.ENROLLED && isAfterApplicationDeadline(lecture, now)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신청 마감 후에는 확정된 신청을 취소할 수 없습니다.");
		}

		lectureEnrollmentRepository.delete(enrollment);
		lectureEnrollmentRepository.flush();

		if (canceledStatus == EnrollmentStatus.ENROLLED && !isAfterApplicationDeadline(lecture, now)) {
			lifecycleHandler.promoteFirstWaitingUser(lecture, now);
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		lifecycleHandler.refreshLectureLifecycle(lecture, now, enrolledCount);

		return new EnrollmentResponse(lecture.getId(), "CANCELED", enrolledCount, waitingCount, null);
	}

	private long countEnrolledInGrade(Long lectureId, Integer grade) {
		return lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.filter(e -> grade.equals(lifecycleHandler.extractGradeFromStudentNumber(e.getUser().getStudentNumber())))
				.count();
	}

	@Transactional(readOnly = true)
	public EnrollmentListResponse getEnrollments(Long lectureId, Long currentUserId, Role currentUserRole) {
		LectureEntity lecture = accessValidator.requireLecture(lectureId);
		accessValidator.validateApprovalVisibility(lecture, currentUserId, currentUserRole);
		List<LectureEnrollmentEntity> allEnrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

		List<EnrollmentUserResponse> enrolled = filterEnrollmentsByStatus(allEnrollments, EnrollmentStatus.ENROLLED);
		List<EnrollmentUserResponse> waiting = filterEnrollmentsByStatus(allEnrollments, EnrollmentStatus.WAITING);

		return new EnrollmentListResponse(enrolled, waiting, List.of());
	}

	private List<EnrollmentUserResponse> filterEnrollmentsByStatus(List<LectureEnrollmentEntity> enrollments,
																   EnrollmentStatus status) {
		return enrollments.stream()
				.filter(e -> e.getStatus() == status)
				.map(this::toEnrollmentUserResponse)
				.toList();
	}

	@Transactional
	public EnrollmentResponse decideWaitingEnrollment(Long lectureId, Long enrollmentUserId, Role currentUserRole) {
		accessValidator.validateAdmin(currentUserRole);
		LectureEntity lecture = accessValidator.requireLectureForUpdate(lectureId);

		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, enrollmentUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대기 신청 내역이 없습니다."));

		if (enrollment.getStatus() != EnrollmentStatus.WAITING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대기 상태의 신청만 수락할 수 있습니다.");
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		enrollment.promoteToEnrolled();

		lifecycleHandler.refreshLectureLifecycle(lecture, SchoolTime.now(), enrolledCount + 1);

		return new EnrollmentResponse(lectureId, EnrollmentStatus.ENROLLED.name(), enrolledCount + 1, waitingCount - 1, enrollment.getRequestedAt());
	}

	private EnrollmentUserResponse toEnrollmentUserResponse(LectureEnrollmentEntity enrollment) {
		UserEntity user = enrollment.getUser();
		return new EnrollmentUserResponse(
				user.getId(),
				user.getName(),
				user.getStudentNumber(),
				enrollment.getRequestedAt()
		);
	}

	private UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
	}
}
