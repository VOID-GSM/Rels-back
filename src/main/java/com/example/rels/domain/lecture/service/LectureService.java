	package com.example.rels.domain.lecture.service;
	
	import java.time.LocalDateTime;
	import java.util.List;
	import java.util.Map;
	import java.util.Set;
	import java.util.function.Function;
	import java.util.stream.Collectors;
	
	import com.example.rels.domain.lecture.dto.request.*;
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
	
		private static final int MIN_CAPACITY = 10;
	
		private final LectureRepository lectureRepository;
		private final LectureEnrollmentRepository lectureEnrollmentRepository;
		private final UserRepository userRepository;
		private final LectureTimeValidator timeValidator;
		private final LectureLifecycleHandler lifecycleHandler;
		private final LectureAccessValidator accessValidator;
	
		public LectureService(LectureRepository lectureRepository,
							  LectureEnrollmentRepository lectureEnrollmentRepository,
							  UserRepository userRepository,
							  LectureTimeValidator timeValidator,
							  LectureLifecycleHandler lifecycleHandler,
							  LectureAccessValidator accessValidator) {
			this.lectureRepository = lectureRepository;
			this.lectureEnrollmentRepository = lectureEnrollmentRepository;
			this.userRepository = userRepository;
			this.timeValidator = timeValidator;
			this.lifecycleHandler = lifecycleHandler;
			this.accessValidator = accessValidator;
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
			Map<Long, String> myEnrollmentStatusByLectureId = getMyEnrollmentStatusByLectureIds(lectures.getContent(), viewerId);
	
			return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId, myEnrollmentStatusByLectureId, viewerId));
		}
	
		@Transactional(readOnly = true)
		public Page<LectureSummaryResponse> getPendingLectures(Role currentUserRole, Pageable pageable) {
			accessValidator.validateAdmin(currentUserRole);
			Page<LectureEntity> lectures = lectureRepository.findAllByApprovalStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING, pageable);
			Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId = getEnrollmentCountsByLectureIds(lectures.getContent());
	
			return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId, Map.of(), null));
		}
	
		@Transactional
		public void updateLectureApproval(Long lectureId, Role currentUserRole, LectureApprovalRequest request) {
			accessValidator.validateAdmin(currentUserRole);
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			lecture.updateApprovalStatus(request.approvalStatus(), request.rejectionReason());
	
			if (request.approvalStatus() == ApprovalStatus.APPROVED) {
				long enrolledCount = lectureEnrollmentRepository.countByLectureIdAndStatus(lectureId, EnrollmentStatus.ENROLLED);
				lifecycleHandler.refreshLectureLifecycle(lecture, SchoolTime.now(), enrolledCount);
			}
		}
	
		@Transactional(readOnly = true)
		public LectureDetailResponse getLectureDetail(Long lectureId, Long userId, Role userRole) {
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateApprovalVisibility(lecture, userId, userRole);
			return toLectureDetail(lecture, userId);
		}
	
		@Transactional(readOnly = true)
		public LectureDetailResponse getLectureDetailForDiscord(Long lectureId) {
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateApprovalVisibility(lecture, null, null);
			return toLectureDetail(lecture, null);
		}
	
		@Transactional
		public LectureDetailResponse updateLecture(Long lectureId, Long userId, Role userRole, LectureUpdateRequest request) {
			validateLectureCapacityRules(request.capacityByGrade(), request.totalCapacity());
			timeValidator.validateApplicationDeadline(request.lectureDate(), request.lectureTime(), request.applicationDeadline());
	
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateCreator(lecture, userId, userRole);
	
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
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateCreator(lecture, userId, userRole);
	
			lectureEnrollmentRepository.deleteByLectureId(lectureId);
			lectureRepository.delete(lecture);
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
	
		@Scheduled(fixedDelayString = "${rels.lecture.lifecycle-sync-delay-ms:60000}")
		@Transactional
		public void syncLectureStatuses() {
			LocalDateTime now = SchoolTime.now();
			List<LectureEntity> lectures = lectureRepository.findAll();
			for (LectureEntity lecture : lectures) {
				lifecycleHandler.refreshLectureLifecycle(lecture, now);
			}
		}
	
		private void validateLectureCapacityRules(Map<Integer, Integer> capacityByGrade, Integer totalCapacity) {
			if (capacityByGrade != null && !capacityByGrade.isEmpty() && totalCapacity != null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원과 전체 정원은 동시에 설정할 수 없습니다.");
			}
	
			if (totalCapacity != null && totalCapacity < MIN_CAPACITY) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전체 정원은 " + MIN_CAPACITY + "명 이상이어야 합니다.");
			}
	
			if (capacityByGrade != null && !capacityByGrade.isEmpty()) {
				int gradeCapacitySum = capacityByGrade.values().stream().mapToInt(Integer::intValue).sum();
				if (gradeCapacitySum < MIN_CAPACITY) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원의 합계는 " + MIN_CAPACITY + "명 이상이어야 합니다.");
				}
				for (Map.Entry<Integer, Integer> e : capacityByGrade.entrySet()) {
					if (e.getValue() == null) {
						throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원 값은 널일 수 없습니다 (학년: " + e.getKey() + ").");
					}
					if (e.getValue() < 0) {
						throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학년별 정원은 0 이상이어야 합니다. (학년: " + e.getKey() + ")");
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
														Map<Long, String> myEnrollmentStatusByLectureId,
														Long viewerId) {
			if (lecture.getCreator() == null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "강의 생성자 정보가 없습니다.");
			}
			long enrolledCount = getEnrollmentCount(enrollmentCountsByLectureId, lecture.getId(), EnrollmentStatus.ENROLLED);
			lifecycleHandler.refreshLectureLifecycle(lecture, SchoolTime.now(), enrolledCount);
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
					lecture.getRejectionReason(),
					enrolledCount,
					waitingCount,
					myEnrollmentStatusByLectureId.get(lecture.getId()),
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
	
		private Map<Long, String> getMyEnrollmentStatusByLectureIds(List<LectureEntity> lectures, Long viewerId) {
			if (viewerId == null || lectures.isEmpty()) return Map.of();
	
			List<Long> lectureIds = lectures.stream().map(LectureEntity::getId).toList();
	
			return lectureEnrollmentRepository.findAllByUserIdAndLectureIdIn(viewerId, lectureIds).stream()
					.collect(Collectors.toMap(
							enrollment -> enrollment.getLecture().getId(),
							enrollment -> enrollment.getStatus().name()));
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
			lifecycleHandler.refreshLectureLifecycle(lecture, SchoolTime.now());
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
					lecture.getRejectionReason(),
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
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateAdmin(currentUserRole);
	
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
							accessValidator.isCreator(lecture, userId),
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
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);
	
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
			LectureEntity lecture = accessValidator.requireLecture(lectureId);
			accessValidator.validateCreatorOrAdmin(lecture, currentUserId, currentUserRole);
	
			List<Long> userIds = requests.stream().map(AttendanceUpdateRequest::userId).toList();
			Map<Long, LectureEnrollmentEntity> enrollmentMap = lectureEnrollmentRepository
					.findAllByLectureIdAndUserIdIn(lectureId, userIds).stream()
					.collect(Collectors.toMap(e -> e.getUser().getId(), Function.identity()));
	
			for (AttendanceUpdateRequest req : requests) {
				LectureEnrollmentEntity enrollment = enrollmentMap.get(req.userId());
				if (enrollment == null) {
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수강 신청 내역이 없습니다. User ID: " + req.userId());
				}
	
				if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "승인된 수강생만 출석을 변경할 수 있습니다.");
				}
	
				enrollment.updateAttendance(req.attendanceStatus());
			}
		}
	
		@Transactional(readOnly = true)
		public Page<LectureSummaryResponse> getLecturesForDiscord(Pageable pageable) {
			Page<LectureEntity> lectures = lectureRepository.findAllByOrderByCreatedAtDesc(pageable);
			Map<Long, Map<EnrollmentStatus, Long>> enrollmentCountsByLectureId = getEnrollmentCountsByLectureIds(lectures.getContent());

			return lectures.map(lecture -> toLectureSummary(lecture, enrollmentCountsByLectureId, Map.of(), null));
		}
	}