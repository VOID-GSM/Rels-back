package com.example.rels.domain.lecture.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.example.rels.domain.lecture.dto.request.AttendanceUpdateRequest;
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

	/**
	 * 서버는 UTC로 돌지만 마감·강연 시각은 사용자가 한국 시간으로 입력한 값이 그대로 저장된다.
	 * 시각을 비교할 때는 UTC now가 아니라 한국 시간 벽시계를 기준으로 삼아야 9시간이 밀리지 않는다.
	 */
	private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Seoul");

	/** 7교시가 끝나는 시각. 수강 신청은 이 시각부터 받는다. */
	private static final LocalTime ENROLLMENT_OPEN_TIME = LocalTime.of(16, 20);

	private static final long CONFIRM_THRESHOLD = 10;
	private static final int MIN_CAPACITY = 10;
	private static final int MAX_CAPACITY = 30;

	private final LectureRepository lectureRepository;
	private final LectureEnrollmentRepository lectureEnrollmentRepository;
	private final UserRepository userRepository;

	public LectureService(LectureRepository lectureRepository,
						  LectureEnrollmentRepository lectureEnrollmentRepository,
						  UserRepository userRepository) {
		this.lectureRepository = lectureRepository;
		this.lectureEnrollmentRepository = lectureEnrollmentRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public LectureDetailResponse createLecture(Long userId, LectureCreateRequest request) {
		validateLectureCapacityRules(request.capacityByGrade(), request.totalCapacity());
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
		lecture = lectureRepository.save(lecture);
		return toLectureDetail(lecture, userId);
	}

	@Transactional(readOnly = true)
	public Page<LectureSummaryResponse> getLectures(Pageable pageable, Long viewerId) {
		Page<LectureEntity> lectures = viewerId == null
				? lectureRepository.findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus.APPROVED, pageable)
				: lectureRepository.findAllByApprovalStatusOrCreatorIdOrderByCreatedAtDesc(ApprovalStatus.APPROVED, viewerId, pageable);
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

		LocalDateTime now = schoolTimeNow();

		LocalDateTime openTime = enrollmentOpenAt(lecture.getCreatedAt());
		if (openTime != null && now.isBefore(openTime)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "수강 신청은 " + openTime.toLocalDate() + " 오후 4시 20분부터 가능합니다.");
		}

		refreshLectureLifecycle(lecture, now);
		if (lecture.getStatus() == LectureStatus.CLOSE) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이미 종료된 강의입니다.");
		}
		if (now.isAfter(lecture.getApplicationDeadline())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신청 마감일이 지났습니다.");
		}
		UserEntity user = requireUser(userId);

		lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.ifPresent(existing -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 강의입니다.");
				});

		Integer userGrade = extractGradeFromStudentNumber(user.getStudentNumber());
		Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade() == null ? Map.of() : lecture.getCapacityByGrade();
		Integer totalCapacity = lecture.getTotalCapacity();
		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		boolean useGradeCapacity = !capacityByGrade.isEmpty();
		boolean isFull;
		if (useGradeCapacity) {
			// 학년을 못 읽거나 배정이 없는 학년은 앉을 자리가 없으므로 대기로 받는다.
			// 마감 뒤 자리가 남으면 그때 순번대로 올라온다.
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

	/** 테스트에서 "지금"을 고정할 수 있도록 열어 둔다. */
	protected LocalDateTime schoolTimeNow() {
		return LocalDateTime.now(SCHOOL_ZONE);
	}

	/** 서버가 UTC로 찍은 시각(createdAt)을 한국 시간 벽시계로 옮긴다. */
	private LocalDateTime toSchoolTime(LocalDateTime serverTime) {
		return serverTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(SCHOOL_ZONE).toLocalDateTime();
	}

	/**
	 * 신청이 열리는 시각(한국 시간). 개설한 날 16:20이 기본이고,
	 * 개설 시점이 이미 그 시각을 넘겼으면 다음 날 16:20이다.
	 */
	private LocalDateTime enrollmentOpenAt(LocalDateTime createdAt) {
		if (createdAt == null) {
			return null;
		}

		LocalDateTime created = toSchoolTime(createdAt);
		LocalDateTime openAt = created.toLocalDate().atTime(ENROLLMENT_OPEN_TIME);

		return created.isBefore(openAt) ? openAt : openAt.plusDays(1);
	}

	/** 학번 "2204"의 맨 앞자리가 학년이다. 두 번째 자리는 반이므로 읽으면 안 된다. */
	private Integer extractGradeFromStudentNumber(String studentNumber) {
		if (studentNumber == null || studentNumber.isEmpty()) return null;
		try {
			return Integer.parseInt(studentNumber.substring(0, 1));
		} catch (Exception e) {
			return null;
		}
	}

	@Transactional
	public EnrollmentResponse cancelEnrollment(Long lectureId, Long userId) {
		LectureEntity lecture = requireLectureForUpdate(lectureId);
		LocalDateTime now = schoolTimeNow();

		// 마감이 지나면 명단이 확정된다. 이때 빠지면 남은 자리를 다시 채울 방법이 없다.
		if (isAfterApplicationDeadline(lecture, now)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "신청 마감 후에는 취소할 수 없습니다.");
		}

		LectureEnrollmentEntity enrollment = lectureEnrollmentRepository.findByLectureIdAndUserId(lectureId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역이 없습니다."));

		EnrollmentStatus canceledStatus = enrollment.getStatus();
		lectureEnrollmentRepository.delete(enrollment);

		if (canceledStatus == EnrollmentStatus.ENROLLED) {
			promoteFirstWaitingUser(lecture, now);
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
		long waitingCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.WAITING);

		return new EnrollmentResponse(lecture.getId(), "CANCELED", enrolledCount, waitingCount, null);
	}

	private void validateLectureCapacityRules(Map<Integer, Integer> capacityByGrade, Integer totalCapacity) {
		if (capacityByGrade != null && !capacityByGrade.isEmpty() && totalCapacity != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원과 전체 정원은 동시에 설정할 수 없습니다.");
		}

		if (totalCapacity != null) {
			if (totalCapacity < MIN_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전체 정원은 " + MIN_CAPACITY + "명 이상이어야 합니다.");
			}
			if (totalCapacity > MAX_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전체 정원은 최대 " + MAX_CAPACITY + "명까지 설정할 수 있습니다.");
			}
		}

		if (capacityByGrade != null && !capacityByGrade.isEmpty()) {
			int gradeCapacitySum = capacityByGrade.values().stream()
					.mapToInt(Integer::intValue)
					.sum();
			if (gradeCapacitySum < MIN_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원의 합계는 " + MIN_CAPACITY + "명 이상이어야 합니다.");
			}
			if (gradeCapacitySum > MAX_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원의 합계는 최대 " + MAX_CAPACITY + "명까지 설정할 수 있습니다.");
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
				if (cap > MAX_CAPACITY) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "한 학년의 정원은 최대 " + MAX_CAPACITY + "명까지 설정할 수 있습니다. (학년: " + grade + ")");
				}
			}
		}
	}

	/**
	 * 자리가 비면 대기자 한 명을 올린다.
	 *
	 * 신청을 받는 동안에는 자리가 남은 학년의 대기자만 올라올 수 있다. 맨 앞 대기자를
	 * 그냥 올리면 1학년이 비운 자리를 2학년이 채워 학년 정원이 넘칠 수 있기 때문이다.
	 * 마감 뒤에는 학년 정원을 더 보지 않고 전체 정원까지 순번대로 올린다.
	 */
	private void promoteFirstWaitingUser(LectureEntity lecture, LocalDateTime now) {
		Long lectureId = lecture.getId();
		List<LectureEnrollmentEntity> enrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

		List<LectureEnrollmentEntity> enrolled = enrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.toList();

		int capacity = resolveTotalCapacity(lecture);
		if (capacity > 0 && enrolled.size() >= capacity) {
			return;
		}

		List<LectureEnrollmentEntity> waiting = sortByRequestedOrder(enrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
				.toList());
		if (waiting.isEmpty()) {
			return;
		}

		Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade();
		boolean useGradeCapacity = capacityByGrade != null && !capacityByGrade.isEmpty()
				&& !isAfterApplicationDeadline(lecture, now);

		if (!useGradeCapacity) {
			waiting.get(0).promoteToEnrolled();
			return;
		}

		for (LectureEnrollmentEntity candidate : waiting) {
			Integer grade = extractGradeFromStudentNumber(candidate.getUser().getStudentNumber());
			Integer gradeCapacity = grade == null ? null : capacityByGrade.get(grade);
			if (gradeCapacity == null) {
				continue;
			}

			long taken = enrolled.stream()
					.filter(e -> grade.equals(extractGradeFromStudentNumber(e.getUser().getStudentNumber())))
					.count();
			if (taken < gradeCapacity) {
				candidate.promoteToEnrolled();
				return;
			}
		}
	}

	private long countEnrolledInGrade(Long lectureId, Integer grade) {
		return lectureEnrollmentRepository.findAllByLectureId(lectureId).stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.filter(e -> grade.equals(extractGradeFromStudentNumber(e.getUser().getStudentNumber())))
				.count();
	}

	/** 신청 순서. 신청 시각이 같으면 먼저 저장된 쪽이 앞선다. */
	private List<LectureEnrollmentEntity> sortByRequestedOrder(List<LectureEnrollmentEntity> enrollments) {
		return enrollments.stream()
				.sorted(Comparator
						.comparing(LectureEnrollmentEntity::getRequestedAt,
								Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(LectureEnrollmentEntity::getId,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
	}

	private boolean isAfterApplicationDeadline(LectureEntity lecture, LocalDateTime now) {
		return lecture.getApplicationDeadline() != null && now.isAfter(lecture.getApplicationDeadline());
	}

	private LectureSummaryResponse toLectureSummary(LectureEntity lecture,
													Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId,
													Long viewerId) {
		if (lecture.getCreator() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
		}
		long enrolledCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.ENROLLED);
		refreshLectureLifecycle(lecture, schoolTimeNow(), enrolledCount);
		long waitingCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.WAITING);

		return new LectureSummaryResponse(
				lecture.getId(),
				lecture.getTitle(),
				lecture.getDescription(),
				lecture.getCreator().getId(),
				lecture.getCreator().getName(),
				lecture.getCreator().getStudentNumber(),
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
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
	}

	private Map<Long, Map<EnrollmentStatus, Long>> getEnrollmentCountsByLectureIds(List<LectureEntity> lectures) {
		if (lectures.isEmpty()) {
			return Map.of();
		}

		List<Long> lectureIds = lectures.stream()
				.map(LectureEntity::getId)
				.toList();

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
		refreshLectureLifecycle(lecture, schoolTimeNow());
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
				lecture.getCapacityByGrade(),
				lecture.getTotalCapacity()
		);
	}

	private void validateApprovalVisibility(LectureEntity lecture, Long viewerId, Role viewerRole) {
		if (lecture.getApprovalStatus() == ApprovalStatus.APPROVED) {
			return;
		}
		if (viewerRole == Role.ADMIN) {
			return;
		}
		if (isCreator(lecture, viewerId)) {
			return;
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "아직 승인되지 않은 강연입니다.");
	}

	private boolean isCreator(LectureEntity lecture, Long viewerId) {
		return viewerId != null && lecture.getCreator() != null
				&& lecture.getCreator().getId().equals(viewerId);
	}

	private String resolveRejectionReason(LectureEntity lecture, Long viewerId) {
		if (lecture.getApprovalStatus() != ApprovalStatus.REJECTED) {
			return null;
		}
		return isCreator(lecture, viewerId) ? lecture.getRejectionReason() : null;
	}

	private UserEntity requireUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
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
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"강의 생성자 정보가 없습니다."
			);
		}

		if (userRole == Role.ADMIN) {
			return;
		}

		if (!lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(
					HttpStatus.FORBIDDEN,
					"강의 작성자만 수정 또는 삭제할 수 있습니다."
			);
		}
	}

	private void validateAdmin(Role userRole) {
		if (userRole != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "학생회 또는 관리자 권한이 필요합니다.");
		}
	}

	@Scheduled(fixedDelayString = "${rels.lecture.lifecycle-sync-delay-ms:60000}")
	@Transactional
	public void syncLectureStatuses() {
		syncLectureStatuses(schoolTimeNow());
	}

	private void syncLectureStatuses(LocalDateTime now) {
		List<LectureEntity> lectures = lectureRepository.findAll();
		for (LectureEntity lecture : lectures) {
			promoteWaitingAfterDeadline(lecture, now);
			refreshLectureLifecycle(lecture, now);
		}
	}

	/**
	 * 신청 마감이 지나면 비어 있는 자리를 대기자로 채운다.
	 * 학년별 정원은 신청을 받는 동안만 적용하고, 마감 뒤에는 전체 정원까지 신청 순서대로 올린다.
	 */
	private void promoteWaitingAfterDeadline(LectureEntity lecture, LocalDateTime now) {
		if (lecture.getId() == null || lecture.getStatus() == LectureStatus.CLOSE) {
			return;
		}

		if (!isAfterApplicationDeadline(lecture, now)) {
			return;
		}

		int capacity = resolveTotalCapacity(lecture);
		if (capacity <= 0) {
			return;
		}

		List<LectureEnrollmentEntity> enrollments = lectureEnrollmentRepository.findAllByLectureId(lecture.getId());
		long enrolledCount = enrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.count();
		if (enrolledCount >= capacity) {
			return;
		}

		List<LectureEnrollmentEntity> waiting = sortByRequestedOrder(enrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
				.toList());

		for (LectureEnrollmentEntity enrollment : waiting) {
			if (enrolledCount >= capacity) {
				break;
			}
			enrollment.promoteToEnrolled();
			enrolledCount++;
		}
	}

	/** 전체 정원. 학년별로 나눈 강연은 학년 정원의 합이 전체 정원이 된다. */
	private int resolveTotalCapacity(LectureEntity lecture) {
		if (lecture.getTotalCapacity() != null) {
			return lecture.getTotalCapacity();
		}

		Map<Integer, Integer> capacityByGrade = lecture.getCapacityByGrade();
		if (capacityByGrade == null || capacityByGrade.isEmpty()) {
			return 0;
		}

		return capacityByGrade.values().stream()
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();
	}

	private void refreshLectureLifecycle(LectureEntity lecture, LocalDateTime now) {
		if (lecture.getId() == null) {
			LocalDateTime lectureEndDateTime = lecture.getLectureEndDateTime();
			if (lecture.getStatus() != LectureStatus.CLOSE && lectureEndDateTime != null && now.isAfter(lectureEndDateTime)) {
				lecture.close();
			}
			return;
		}

		long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lecture.getId(), EnrollmentStatus.ENROLLED);
		refreshLectureLifecycle(lecture, now, enrolledCount);
	}

	private void refreshLectureLifecycle(LectureEntity lecture, LocalDateTime now, long enrolledCount) {
		if (lecture.getStatus() == LectureStatus.CLOSE) {
			return;
		}

		LocalDateTime lectureEndDateTime = lecture.getLectureEndDateTime();
		if (lectureEndDateTime != null && now.isAfter(lectureEndDateTime)) {
			lecture.close();
			return;
		}

		if (lecture.getStatus() != LectureStatus.OPEN) {
			return;
		}

		if (lecture.getApplicationDeadline() != null && now.isAfter(lecture.getApplicationDeadline())) {
			if (enrolledCount >= CONFIRM_THRESHOLD) {
				lecture.confirm();
				return;
			}
			lecture.setStatus(LectureStatus.UNCONFIRMED);
		}
	}

	@Transactional(readOnly = true)
	public EnrollmentListResponse getEnrollments(Long lectureId) {
		requireLecture(lectureId);

		List<LectureEnrollmentEntity> allEnrollments = lectureEnrollmentRepository.findAllByLectureId(lectureId);

		List<EnrollmentUserResponse> enrolled = allEnrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
				.map(this::toEnrollmentUserResponse)
				.toList();

		List<EnrollmentUserResponse> waiting = allEnrollments.stream()
				.filter(e -> e.getStatus() == EnrollmentStatus.WAITING)
				.map(this::toEnrollmentUserResponse)
				.toList();

		return new EnrollmentListResponse(enrolled, waiting);
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

		List<LectureEntity> myLectures = lectureRepository.findAllByCreatorIdOrderByCreatedAtDesc(userId);
		List<MyCreatedLectureResponse> createdLectures = myLectures.stream()
				.map(lecture -> new MyCreatedLectureResponse(
						lecture.getId(),
						lecture.getTitle(),
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
		if (userRole == Role.ADMIN) {
			return;
		}
		if (lecture.getCreator() == null || !lecture.getCreator().getId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "강의 작성자 또는 관리자만 접근 가능합니다.");
		}
	}
}