package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.rels.domain.lecture.dto.request.AttendanceUpdateRequest;
import com.example.rels.domain.lecture.dto.request.EnrollmentDecisionRequest;
import com.example.rels.domain.lecture.dto.request.LectureApprovalRequest;
import com.example.rels.domain.lecture.dto.request.LectureCreateRequest;
import com.example.rels.domain.lecture.dto.request.LectureUpdateRequest;
import com.example.rels.domain.lecture.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.rels.domain.user.entity.Role;
import com.example.rels.domain.user.entity.UserEntity;
import com.example.rels.domain.user.repository.UserRepository;
import com.example.rels.domain.lecture.entity.ApprovalStatus;
import com.example.rels.domain.lecture.entity.EnrollmentStatus;
import com.example.rels.domain.lecture.entity.LectureEnrollmentEntity;
import com.example.rels.domain.lecture.entity.LectureEntity;
import com.example.rels.domain.lecture.entity.LectureStatus;
import com.example.rels.domain.lecture.repository.LectureEnrollmentCountProjection;
import com.example.rels.domain.lecture.repository.LectureEnrollmentRepository;
import com.example.rels.domain.lecture.repository.LectureRepository;

@Service
public class LectureService {

	private static final long CONFIRM_THRESHOLD = 10;
	private static final int MIN_CAPACITY = 10;

	private final LectureRepository lectureRepository;
	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final UserRepository userRepository;
	private final LectureTimeValidator timeValidator;
	private final LectureLifecycleHandler lifecycleHandler;

	public LectureService(LectureRepository lectureRepository,
						  LectureEnrollmentRepository lectureEnrollmentRepository,
						  UserRepository userRepository,
						  LectureTimeValidator timeValidator,
						  LectureLifecycleHandler lifecycleHandler) {
		this.lectureRepository = lectureRepository;
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.userRepository = userRepository;
		this.timeValidator = timeValidator;
		this.lifecycleHandler = lifecycleHandler;
	}

	@Transactional
	public LectureDetailResponse createLecture(Long userId, LectureCreateRequest request) {
		validateLectureCapacityRules(request.capacityByGrade(), request.totalCapacity());
		timeValidator.validateApplicationDeadline(request.lectureDate(), request.lectureTime(), request.applicationDeadline());

		UserEntity creator = requireUser(userId);

		LectureEntity lecture = new LectureEntity(
				request.title(),
				request.description(),
				creator,
				request.lectureLocation(),
				request.lectureDate(),
				request.lectureTime(),
				request.applicationDeadline(),
				request.totalCapacity()
		);
		lecture.setCapacityByGrade(request.capacityByGrade());
		lecture.updateSpeakers(resolveSpeakers(request.speakerIds()));
		lecture = lectureRepository.save(lecture);
		return toLectureDetail(lecture, userId);
	}

	@Transactional(readOnly = true)
	public Page<LectureSummaryResponse> getLectures(Pageable pageable, Long viewerId) {
		Page<LectureEntity> lectures = viewerId == null
				? lectureRepository.findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus.APPROVED, pageable)
				: lectureRepository.findVisibleToUser(ApprovalStatus.APPROVED, viewerId, pageable);
		Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId = getEnrollmentCountsByLectureIds(lectures.getContent());

		return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId, viewerId));
	}

	@Transactional(readOnly = true)
	public Page<LectureSummaryResponse> getPendingLectures(Role currentUserRole, Pageable pageable) {
		validateAdmin(currentUserRole);
		Page<LectureEntity> lectures = lectureRepository.findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING, pageable);
		Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId = getEnrollmentCountsByLectureIds(lectures.getContent());

		return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId, null));
	}

	@Transactional
	public void updateLectureApproval(Long lectureId, Role currentUserRole, LectureApprovalRequest request) {
		validateAdmin(currentUserRole);
		LectureEntity lecture = requireLecture(lectureId);
		lecture.updateApprovalStatus(request.approvalStatus(), request.rejectionReason());
	}

	@Transactional(readOnly = true)
	public LectureDetailResponse getLectureDetail(Long lectureId, Long userId, Role userRole) {
		LectureEntity lecture = requireLecture(lectureId);
		validateApprovalVisibility(lecture, userId, userRole);
		return toLectureDetail(lecture, userId);
	}

	@Transactional(readOnly = true)
	public LectureDetailResponse getLectureDetailForDiscord(Long lectureId) {
		LectureEntity lecture = requireLecture(lectureId);
		validateApprovalVisibility(lecture, null, null);
		return toLectureDetail(lecture, null);
	}

	@Transactional
	public LectureDetailResponse updateLecture(Long lectureId, Long userId, Role userRole, LectureUpdateRequest request) {
		validateLectureCapacityRules(request.capacityByGrade(), request.totalCapacity());
		timeValidator.validateApplicationDeadline(request.lectureDate(), request.lectureTime(), request.applicationDeadline());

		LectureEntity lecture = requireLecture(lectureId);
		validateCreator(lecture, userId, userRole);

		lecture.updateAllDetails(
				request.title(),
				request.description(),
				request.capacityByGrade(),
				request.totalCapacity(),
				request.lectureLocation(),
				request.lectureDate(),
				request.lectureTime(),
				request.applicationDeadline()
		);
		lecture.updateSpeakers(resolveSpeakers(request.speakerIds()));

		return toLectureDetail(lecture, userId);
	}

	@Transactional
	public void deleteLecture(Long lectureId, Long userId, Role userRole) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreator(lecture, userId, userRole);

		lectureEnrollmentRepository.deleteByLectureId(lectureId);
		lectureRepository.delete(lecture);
	}

	@Transactional
	public EnrollmentResponse enroll(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		if (lecture.getApprovalStatus() != ApprovalStatus.APPROVED) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "승인된 강연만 수강 신청할 수 있습니다.");
		}

		LocalDateTime now = LocalDateTime.now();

		LocalDateTime applicationOpenReference = lecture.getApprovedAt() != null
				? lecture.getApprovedAt() : lecture.getCreatedAt();
		timeValidator.validateApplicationTime(applicationOpenReference, lecture.getApplicationDeadline(), now);
		boolean isAfterApplicationDeadline = lecture.getApplicationDeadline() != null
				&& now.isAfter(lecture.getApplicationDeadline());

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

		if (status == EnrollmentStatus.ENROLLED && lecture.getStatus() == LectureStatus.OPEN
				&& enrolledCount + 1 >= CONFIRM_THRESHOLD) {
			lecture.confirm();
		}

		long nextEnrolledCount = status == EnrollmentStatus.ENROLLED ? enrolledCount + 1 : enrolledCount;
		long nextWaitingCount = status == EnrollmentStatus.WAITING ? waitingCount + 1 : waitingCount;

		return new EnrollmentResponse(lectureId, status.name(), nextEnrolledCount, nextWaitingCount, savedEnrollment.getRequestedAt());
	}

	@Transactional
	public EnrollmentResponse cancelEnrollment(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역이 없습니다."));

		EnrollmentStatus canceledStatus = enrollment.getStatus();
		lectureEnrollmentRepository.delete(enrollment);

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		return new EnrollmentResponse(lecture.getId(), "CANCELED", enrolledCount, waitingCount, null);
	}

	@Scheduled(fixedDelayString = "${rels.lecture.lifecycle-sync-delay-ms:60000}")
	@Transactional
	public void syncLectureStatuses() {
		LocalDateTime now = LocalDateTime.now();
		List<LectureEntity> lectures = lectureRepository.findAll();
		for (LectureEntity lecture : lectures) {
			lifecycleHandler.refreshLectureLifecycle(lecture, now);
		}
	}

	private void validateLectureCapacityRules(Map<Integer, Integer> capacityByGrade, Integer totalCapacity) {
		if (capacityByGrade != null && !capacityByGrade.isEmpty() && totalCapacity != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원과 전체 정원은 동시에 설정할 수 없습니다.");
		}

		if (totalCapacity != null) {
			if (totalCapacity < MIN_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전체 정원은 " + MIN_CAPACITY + "명 이상이어야 합니다.");
			}
		}

		if (capacityByGrade != null && !capacityByGrade.isEmpty()) {
			int gradeCapacitySum = capacityByGrade.values().stream().mapToInt(Integer::intValue).sum();
			if (gradeCapacitySum < MIN_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원의 합계는 " + MIN_CAPACITY + "명 이상이어야 합니다.");
			}
			for (Map.Entry<Integer, Integer> e : capacityByGrade.entrySet()) {
				Integer grade = e.getKey();
				Integer cap = e.getValue();
				if (cap == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원 값은 널일 수 없습니다 (학년: " + grade + ").");
				}
				if (cap < 0) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원은 0 이상이어야 합니다. (학년: " + grade + ")");
				}
			}
		}
	}

	private long countEnrolledInGrade(Long lectureId, Integer grade) {
		return lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.filter(e -> grade.equals(lifecycleHandler.extractGradeFromStudentNumber(e.getUser().getStudentNumber())))
				.count();
	}

	private LectureSummaryResponse toLectureSummary(LectureEntity lecture,
													Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId,
													Long viewerId) {
		if (lecture.getCreator() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
		}
		long enrolledCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.ENROLLED);
		lifecycleHandler.refreshLectureLifecycle(lecture, LocalDateTime.now(), enrolledCount);
		long waitingCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.WAITING);

		return new LectureSummaryResponse(
				lecture.getId(),
				lecture.getTitle(),
				lecture.getDescription(),
				lecture.getCreator().getId(),
				lecture.getCreator().getName(),
				lecture.getCreator().getStudentNumber(),
				toSpeakerResponses(lecture),
				lecture.getStatus().name(),
				lecture.getApprovalStatus().name(),
				resolveRejectionReason(lecture, viewerId),
				enrolledCount,
				waitingCount,
				lecture.getLectureLocation(),
				lecture.getLectureDate(),
				lecture.getLectureTime(),
				lecture.getApplicationDeadline(),
				lecture.getCreatedAt(),
				lecture.getApprovedAt(),
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
	}

	private Map<Long, Map<EnrollmentStatus, Long>> getEnrollmentCountsByLectureIds(List<LectureEntity> lectures) {
		if (lectures.isEmpty()) return Map.of();

		List<Long> lectureIds = lectures.stream().map(LectureEntity::getId).toList();

		return lectureEnrollmentRepository.countEnrollmentsByLectureIds(lectureIds).stream()
				.collect(Collectors.groupingBy(
						LectureEnrollmentCountProjection::getLectureId,
						Collectors.toMap(
								LectureEnrollmentCountProjection::getStatus,
								LectureEnrollmentCountProjection::getEnrollmentCount)));
	}

	private long getEnrollmentCount(Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId,
									Long lectureId, EnrollmentStatus status) {
		return enrollmentCountsByLectureId.getOrDefault(lectureId, Map.of())
				.getOrDefault(status, 0L);
	}

	private LectureDetailResponse toLectureDetail(LectureEntity lecture, Long userId) {
		if (lecture.getCreator() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
		}
		lifecycleHandler.refreshLectureLifecycle(lecture, LocalDateTime.now());
		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.WAITING);

		String myEnrollmentStatus = lectureEnrollmentRepository.findByLectureIdAndUserId(lecture.getId(), userId)
				.map(enrollment -> enrollment.getStatus().name())
				.orElse(null);

		return new LectureDetailResponse(
				lecture.getId(),
				lecture.getTitle(),
				lecture.getDescription(),
				lecture.getCreator().getId(),
				lecture.getCreator().getName(),
				lecture.getCreator().getStudentNumber(),
				toSpeakerResponses(lecture),
				lecture.getStatus().name(),
				lecture.getApprovalStatus().name(),
				resolveRejectionReason(lecture, userId),
				enrolledCount,
				waitingCount,
				myEnrollmentStatus,
				lecture.getLectureLocation(),
				lecture.getLectureDate(),
				lecture.getLectureTime(),
				lecture.getApplicationDeadline(),
				lecture.getCreatedAt(),
				lecture.getApprovedAt(),
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
	}

	private void validateApprovalVisibility(LectureEntity lecture, Long viewerId, Role viewerRole) {
		if (lecture.getApprovalStatus() == ApprovalStatus.APPROVED) return;
		if (viewerRole == Role.ADMIN) return;
		if (isCreator(lecture, viewerId) || lecture.isSpeaker(viewerId)) return;
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "아직 승인되지 않은 강연입니다.");
	}

	private boolean isCreator(LectureEntity lecture, Long viewerId) {
		return viewerId != null && lecture.getCreator() != null
				&& lecture.getCreator().getId().equals(viewerId);
	}

	private String resolveRejectionReason(LectureEntity lecture, Long viewerId) {
		if (lecture.getApprovalStatus() != ApprovalStatus.REJECTED) return null;
		return (isCreator(lecture, viewerId) || lecture.isSpeaker(viewerId)) ? lecture.getRejectionReason() : null;
	}

	private UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
	}

	private Set<UserEntity> resolveSpeakers(Set<Long> speakerIds) {
		if (speakerIds == null || speakerIds.isEmpty()) {
			return Set.of();
		}
		List<UserEntity> speakers = userRepository.findAllById(speakerIds);
		if (speakers.size() != speakerIds.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록되지 않은 연사자가 포함되어 있습니다.");
		}
		return Set.copyOf(speakers);
	}

	private List<LectureSpeakerResponse> toSpeakerResponses(LectureEntity lecture) {
		return lecture.getSpeakers().stream()
				.map(speaker -> new LectureSpeakerResponse(speaker.getId(), speaker.getName(), speaker.getStudentNumber()))
				.toList();
	}

	private LectureEntity requireLecture(Long lectureId) {
		return lectureRepository.findById(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	private LectureEntity requireLectureForUpdate(Long lectureId) {
		return lectureRepository.findByIdForUpdate(lectureId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."));
	}

	private void validateCreator(LectureEntity lecture, Long userId, Role userRole) {
		if (lecture.getCreator() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
		}
		if (userRole == Role.ADMIN) return;
		if (!lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자만 수정 또는 삭제할 수 있습니다.");
		}
	}

	private void validateAdmin(Role userRole) {
		if (userRole != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생회 또는 관리자 권한이 필요합니다.");
		}
	}

	/**
	 * 신청자·대기자 명단은 누가 신청했는지 보고 판단하는 정보라 학생 누구나 볼 수 있다.
	 * 다만 누가 거절됐는지는 명단에 뿌릴 정보가 아니라서 개설자와 학생회에게만 내려준다.
	 */
	@Transactional(readOnly = true)
	public EnrollmentListResponse getEnrollments(Long lectureId, Long currentUserId, Role currentUserRole) {
		LectureEntity lecture = requireLecture(lectureId);
		validateApprovalVisibility(lecture, currentUserId, currentUserRole);
		List<LectureEnrollmentEntity> allEnrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

		List<EnrollmentUserResponse> enrolled = filterEnrollmentsByStatus(allEnrollments, EnrollmentStatus.ENROLLED);
		List<EnrollmentUserResponse> waiting = filterEnrollmentsByStatus(allEnrollments, EnrollmentStatus.WAITING);
		List<EnrollmentUserResponse> rejected = canManageEnrollments(lecture, currentUserId, currentUserRole)
				? filterEnrollmentsByStatus(allEnrollments, EnrollmentStatus.REJECTED)
				: List.of();

		return new EnrollmentListResponse(enrolled, waiting, rejected);
	}

	private List<EnrollmentUserResponse> filterEnrollmentsByStatus(List<LectureEnrollmentEntity> enrollments,
			EnrollmentStatus status) {
		return enrollments.stream()
				.filter(e -> e.getStatus() == status)
				.map(this::toEnrollmentUserResponse)
				.toList();
	}

	@Transactional
	public EnrollmentResponse decideWaitingEnrollment(Long lectureId, Long enrollmentUserId, Long currentUserId,
													  Role currentUserRole, EnrollmentDecisionRequest request) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);
		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, enrollmentUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대기 신청 내역이 없습니다."));
		if (enrollment.getStatus() != EnrollmentStatus.WAITING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "대기 상태의 신청만 수락 또는 거절할 수 있습니다.");
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);
		if (request.approved()) {
			enrollment.promoteToEnrolled();
			return new EnrollmentResponse(lectureId, EnrollmentStatus.ENROLLED.name(), enrolledCount + 1, waitingCount - 1, enrollment.getRequestedAt());
		}

		enrollment.reject();
		return new EnrollmentResponse(lectureId, EnrollmentStatus.REJECTED.name(), enrolledCount, waitingCount - 1, enrollment.getRequestedAt());
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

	@Transactional(readOnly = true)
	public MyLecturesResponse getMyLectures(Long userId) {
		requireUser(userId);

		List<LectureEnrollmentEntity> myEnrollments = lectureEnrollmentRepository.findAllByUserId(userId);
		List<MyEnrolledLectureResponse> enrolledLectures = myEnrollments.stream()
				.map(enrollment -> {
					if (enrollment.getLecture().getCreator() == null) {
						throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
					}
					return new MyEnrolledLectureResponse(
							enrollment.getLecture().getId(),
							enrollment.getLecture().getTitle(),
							enrollment.getLecture().getStatus().name(),
							enrollment.getStatus().name(),
							enrollment.getLecture().getCreator().getName(),
							enrollment.getLecture().getCreator().getStudentNumber(),
							enrollment.getLecture().getLectureLocation(),
							enrollment.getLecture().getLectureDate(),
							enrollment.getLecture().getLectureTime(),
							enrollment.getLecture().getApplicationDeadline(),
							enrollment.getRequestedAt()
					);
				})
				.toList();

		List<LectureEntity> myLectures = lectureRepository.findAllBySpeakerIdOrderByCreatedAtDesc(userId);
		List<MyCreatedLectureResponse> createdLectures = myLectures.stream()
				.map(lecture -> new MyCreatedLectureResponse(
						lecture.getId(),
						lecture.getTitle(),
						isCreator(lecture, userId),
						lecture.getStatus().name(),
						lecture.getApprovalStatus().name(),
						lecture.getRejectionReason(),
						lecture.getLectureLocation(),
						lecture.getLectureDate(),
						lecture.getLectureTime(),
						lecture.getApplicationDeadline(),
						lecture.getCreatedAt()
				))
				.toList();

		return new MyLecturesResponse(enrolledLectures, createdLectures);
	}

	@Transactional(readOnly = true)
	public List<LectureAttendanceResponse> getAttendanceList(Long lectureId, Long currentUserId, Role currentUserRole) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);

		return lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.map(e -> new LectureAttendanceResponse(
						e.getUser().getId(),
						e.getUser().getName(),
						e.getUser().getStudentNumber(),
						e.getAttendanceStatus(),
						e.getAttendedAt()
				))
				.toList();
	}

	@Transactional
	public void updateAttendances(Long lectureId, Long currentUserId, Role currentUserRole, List<AttendanceUpdateRequest> requests) {
		LectureEntity lecture = requireLecture(lectureId);
		validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);

		for (AttendanceUpdateRequest req : requests) {
			LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, req.userId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수강 신청 내역이 없습니다. User ID: " + req.userId()));

			if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "승인된 수강생만 출석을 변경할 수 있습니다.");
			}

			enrollment.updateAttendance(req.attendanceStatus());
		}
	}

	private void validateCreatorOrAdmin(LectureEntity lecture, Long userId, Role userRole) {
		if (!canManageEnrollments(lecture, userId, userRole)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자 또는 관리자만 접근 가능합니다.");
		}
	}

	/** 대기자를 수락·거절하고 거절 명단까지 볼 수 있는 사람인지. */
	private boolean canManageEnrollments(LectureEntity lecture, Long userId, Role userRole) {
		if (userRole == Role.ADMIN) return true;
		return isCreator(lecture, userId);
	}
}
